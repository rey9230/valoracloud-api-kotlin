package com.valoracloud.api.billing.service

import com.stripe.Stripe
import com.stripe.exception.SignatureVerificationException
import com.stripe.model.Event
import com.stripe.model.PaymentIntent
import com.stripe.net.Webhook
import com.valoracloud.api.common.model.OrderStatus
import com.valoracloud.api.common.model.PaymentMethod
import com.valoracloud.api.config.*
import com.valoracloud.api.entity.Invoice
import com.valoracloud.api.entity.Order
import com.valoracloud.api.entity.WebhookEvent
import com.valoracloud.api.facebook.AddPaymentInfoParams
import com.valoracloud.api.facebook.FacebookConversionsService
import com.valoracloud.api.facebook.PurchaseParams
import com.valoracloud.api.notifications.service.NotificationsService
import com.valoracloud.api.shkeeper.SHKeeperCallback
import com.valoracloud.api.shkeeper.SHKeeperService
import jakarta.persistence.EntityManager
import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BillingService(
        private val orderRepo: OrderRepository,
        private val invoiceRepo: InvoiceRepository,
        private val webhookEventRepo: WebhookEventRepository,
        private val serverRepo: ServerRepository,
        private val userRepo: UserRepository,
        private val shkeeper: SHKeeperService,
        private val notifications: NotificationsService,
        private val facebookConversions: FacebookConversionsService,
        private val provisioningProcessor:
                com.valoracloud.api.provisioning.processor.ProvisioningProcessor,
        private val entityManager: EntityManager,
        @Value("\${stripe.secret-key:}") stripeSecretKey: String,
        @Value("\${stripe.webhook-secret:}") private val webhookSecret: String,
        @Value("\${stripe.enabled:true}") private val stripeEnabled: Boolean,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    init {
        if (stripeSecretKey.isNotBlank() && stripeEnabled) {
            Stripe.apiKey = stripeSecretKey
        }
    }

    // ─── Webhook handling ───────────────────────────────

    @Transactional
    fun handleWebhook(rawBody: String, signature: String): Map<String, Any> {
        if (!stripeEnabled) {
            log.warn("Webhook received but STRIPE_ENABLED=false, ignoring")
            return mapOf("received" to true, "bypassed" to true)
        }

        if (webhookSecret.isBlank()) {
            log.error("Webhook received but STRIPE_WEBHOOK_SECRET is empty")
            throw com.valoracloud.api.common.exceptions.BadRequestException(
                    "Stripe webhook is not configured"
            )
        }

        // 1. Verify Stripe signature
        val event: Event =
                try {
                    Webhook.constructEvent(rawBody, signature, webhookSecret)
                } catch (e: SignatureVerificationException) {
                    log.error("Webhook signature verification failed: ${e.message}")
                    throw com.valoracloud.api.common.exceptions.BadRequestException(
                            "Invalid webhook signature"
                    )
                }

        // 2. Idempotency check
        val existing = webhookEventRepo.findByStripeEventId(event.id)
        if (existing?.processed == true) {
            log.info("Event ${event.id} already processed, skipping")
            return mapOf("received" to true)
        }

        // 3. Store event only once so Stripe retries don't hit unique-constraint errors.
        val storedEvent =
                existing
                        ?: webhookEventRepo.save(
                                WebhookEvent(
                                        stripeEventId = event.id,
                                        eventType = event.type,
                                        eventSource = "stripe",
                                )
                        )

        // 4. Route event
        try {
            when (event.type) {
                "payment_intent.succeeded" -> handlePaymentSucceeded(event)
                "payment_intent.payment_failed" -> handlePaymentFailed(event)
                "charge.refunded" -> handleChargeRefunded(event)
                "charge.dispute.created" -> handleDisputeCreated(event)
                else -> log.info("Unhandled event type: ${event.type}")
            }

            // Mark processed
            storedEvent.processed = true
            webhookEventRepo.save(storedEvent)
        } catch (e: Exception) {
            log.error("Error processing event ${event.id}: ${e.message}", e)
            throw e
        }

        return mapOf("received" to true)
    }

    // ─── Event handlers ─────────────────────────────────

    private fun handlePaymentSucceeded(event: Event) {
        log.info("💰 Processing payment_intent.succeeded")
        val paymentIntent =
                event.dataObjectDeserializer.getObject().orElse(null) as? PaymentIntent ?: run {
                    log.error("❌ Failed to deserialize PaymentIntent from event")
                    return
                }

        log.info("💳 PaymentIntent ID: ${paymentIntent.id}, Metadata: ${paymentIntent.metadata}")
        
        val orderId =
                paymentIntent.metadata["orderId"]
                        ?: run {
                            log.warn("⚠️ PaymentIntent succeeded without orderId in metadata")
                            return
                        }
        
        log.info("🎯 Processing order: $orderId")

        val order = orderRepo.findById(orderId).orElse(null)
        if (order == null) {
            log.error("❌ Order $orderId not found in database!")
            return
        }

        log.info("📋 Order found - Current status: ${order.status}, User: ${order.userId}")

        // Allow recovery: SUCCEEDED payment can reactivate FAILED order
        if (order.status !in listOf(OrderStatus.PENDING_PAYMENT, OrderStatus.FAILED)) {
            log.warn("⚠️ Order $orderId already in status ${order.status}, skipping payment processing")
            return
        }

        log.info("💾 Updating order status to PAID...")
        // Update order to PAID
        order.status = OrderStatus.PAID
        orderRepo.save(order)
        log.info("✅ Order status updated to PAID")

        log.info("🧾 Creating invoice...")
        // Create invoice
        val invoice = invoiceRepo.save(
                Invoice(
                        userId = order.userId,
                        orderId = order.id,
                        amount = order.totalAmount,
                        currency = "USD",
                        paymentMethod = PaymentMethod.STRIPE,
                        stripeInvoiceId = paymentIntent.id,
                        paidAt = Instant.now(),
                )
        )
        log.info("✅ Invoice created: ${invoice.id}")

        // Notify user (async to avoid blocking webhook response)
        log.info("📧 Looking up user for notification...")
        val user = userRepo.findById(order.userId).orElse(null)
        if (user != null) {
            log.info("📧 Scheduling payment confirmation email to: ${user.email} (async)")

            // Fire-and-forget email - runs in separate thread, won't block webhook
            java.util.concurrent.CompletableFuture.runAsync {
                try {
                    log.info("📧 Sending email async to ${user.email}...")
                    notifications.sendPaymentConfirmationEmail(
                            email = user.email,
                            orderId = order.id,
                            amount = order.totalAmount.toDouble(),
                            language = user.language,
                            userId = order.userId,
                    )
                    log.info("✅ Email sent successfully to ${user.email}")
                } catch (e: Exception) {
                    log.warn("⚠️ Email sending failed (non-critical, provisioning continues): ${e.message}")
                }
            }
            log.info("✅ Email task scheduled, continuing with provisioning...")
        } else {
            log.warn("⚠️ User ${order.userId} not found - skipping email notification")
        }

        // Track Facebook events (fire-and-forget)
        log.info("📊 Tracking Facebook conversions...")
        runCatching {
            facebookConversions.trackAddPaymentInfo(
                    AddPaymentInfoParams(orderId = order.id, email = user?.email)
            )
            log.info("✅ Facebook AddPaymentInfo tracked")
        }.onFailure {
            log.warn("⚠️ Facebook AddPaymentInfo tracking failed: ${it.message}")
        }
        runCatching {
            facebookConversions.trackPurchase(
                    PurchaseParams(
                            orderId = order.id,
                            value = order.totalAmount.toDouble(),
                            currency = "USD",
                            email = user?.email
                    )
            )
            log.info("✅ Facebook Purchase tracked")
        }.onFailure {
            log.warn("⚠️ Facebook Purchase tracking failed: ${it.message}")
        }

        // CRITICAL: Force flush to ensure order changes are committed before async dispatch
        log.info("💾 Flushing entity manager to commit order changes...")
        entityManager.flush()
        entityManager.clear()
        log.info("✅ Entity manager flushed - DB transaction will commit")

        // Dispatch provisioning (runs async in separate thread)
        log.info("🚀 Dispatching provisioning for order ${order.id}...")
        try {
            dispatchProvisioning(order)
            log.info("✅✅✅ Order ${order.id} FULLY PROCESSED - provisioning dispatched!")
        } catch (e: Exception) {
            log.error("❌ Provisioning dispatch failed: ${e.message}", e)
            throw e
        }
    }

    private fun handlePaymentFailed(event: Event) {
        val paymentIntent =
                event.dataObjectDeserializer.getObject().orElse(null) as? PaymentIntent ?: return

        val orderId = paymentIntent.metadata["orderId"] ?: return

        orderRepo
                .findByStatus(OrderStatus.PENDING_PAYMENT)
                .filter { it.id == orderId }
                .forEach {
                    it.status = OrderStatus.FAILED
                    orderRepo.save(it)
                }

        log.info("Order $orderId payment failed")
    }

    private fun handleChargeRefunded(event: Event) {
        // charge.refunded — find order by stripePaymentId
        val charge =
                event.dataObjectDeserializer.getObject().orElse(null) as? com.stripe.model.Charge
                        ?: return

        val paymentIntentId = charge.paymentIntent

        if (paymentIntentId.isNullOrBlank()) return

        val orders = orderRepo.findAll().filter { it.stripePaymentId == paymentIntentId }
        for (order in orders) {
            order.status = OrderStatus.CANCELLED
            orderRepo.save(order)
            log.info("Order ${order.id} refunded and cancelled")
        }
    }

    private fun handleDisputeCreated(event: Event) {
        log.warn("Dispute created: ${event.id}")
        // TODO: Notify admin, optionally suspend related server
    }

    // ─── Crypto webhook ─────────────────────────────────

    @Transactional
    fun handleCryptoWebhook(apiKey: String, payload: SHKeeperCallback): Map<String, Any> {
        // 1. Authenticate
        if (apiKey.isBlank() || apiKey != shkeeper.getApiKey()) {
            throw com.valoracloud.api.common.exceptions.UnauthorizedException(
                    "Invalid SHKeeper API key"
            )
        }

        val orderId = payload.externalId
        log.info(
                "Crypto callback received for order $orderId: status=${payload.status}, paid=${payload.paid}"
        )

        // 2. Idempotency
        val triggerTx = payload.transactions.find { it.trigger }
        val eventKey =
                if (triggerTx != null) "$orderId:${triggerTx.txid}"
                else "$orderId:${payload.status}"

        val existing =
                webhookEventRepo.findByExternalId(eventKey).any {
                    it.eventSource == "crypto" && it.processed
                }
        if (existing) {
            log.info("Crypto event $eventKey already processed, skipping")
            return mapOf("received" to true)
        }

        // 3. Store event
        webhookEventRepo.save(
                WebhookEvent(
                        externalId = eventKey,
                        eventSource = "crypto",
                        eventType = payload.status,
                )
        )

        // 4. Only proceed for fully paid invoices
        if (!payload.paid || payload.status !in listOf("PAID", "OVERPAID")) {
            log.info("Order $orderId crypto payment not yet complete (${payload.status}), ignoring")
            return mapOf("received" to true)
        }

        val order = orderRepo.findById(orderId).orElse(null)
        if (order == null || order.status != OrderStatus.PENDING_PAYMENT) {
            log.warn("Order $orderId not found or not pending")
            return mapOf("received" to true)
        }

        // 5. Mark order PAID and create invoice
        order.status = OrderStatus.PAID
        orderRepo.save(order)

        invoiceRepo.save(
                Invoice(
                        userId = order.userId,
                        orderId = order.id,
                        amount = order.totalAmount,
                        currency = "USD",
                        paymentMethod = PaymentMethod.CRYPTO,
                        cryptoTxId = triggerTx?.txid,
                        cryptoCurrency = payload.crypto,
                        cryptoAmount = triggerTx?.amountCrypto ?: payload.balanceCrypto,
                        paidAt = Instant.now(),
                )
        )

        // 6. Mark event processed
        webhookEventRepo.findByExternalId(eventKey).forEach {
            it.processed = true
            webhookEventRepo.save(it)
        }

        // 7. CRITICAL: Flush before async dispatch (same as Stripe webhook)
        log.info("💾 Flushing entity manager for crypto webhook...")
        entityManager.flush()
        entityManager.clear()
        log.info("✅ Entity manager flushed - crypto order changes committed")

        // 8. Dispatch provisioning
        log.info("🚀 Dispatching provisioning for crypto-paid order $orderId...")
        dispatchProvisioning(order)

        // Track Facebook
        val cryptoUser = userRepo.findById(order.userId).orElse(null)
        runCatching {
            facebookConversions.trackPurchase(
                    PurchaseParams(
                            orderId = order.id,
                            value = order.totalAmount.toDouble(),
                            currency = "USD",
                            email = cryptoUser?.email
                    )
            )
        }

        log.info("✅✅✅ Order $orderId crypto-paid, provisioning dispatched!")
        return mapOf("received" to true)
    }

    // ─── Helpers ────────────────────────────────────────

    private fun dispatchProvisioning(order: Order) {
        log.info("🔧 Creating provisioning job for order ${order.id}")
        log.info("   Plan: ${order.planId}, Region: ${order.region}, OS: ${order.os}")

        try {
            provisioningProcessor.provisionServer(
                    ProvisionJobData(
                            orderId = order.id,
                            planId = order.planId ?: "",
                            userId = order.userId,
                            region = order.region,
                            os = order.os,
                            hostname = order.hostname,
                    )
            )
            log.info("✅ Provisioning processor called successfully")
        } catch (e: Exception) {
            log.error(
                    "❌❌❌ Provisioning dispatch FAILED for order ${order.id}. Stripe will retry. Error: ${e.message}",
                    e
            )
            throw e
        }
    }
}

data class ProvisionJobData(
        val orderId: String,
        val planId: String,
        val userId: String,
        val region: String,
        val os: String,
        val hostname: String?,
)
