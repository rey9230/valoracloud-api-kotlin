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
import com.valoracloud.api.facebook.FacebookConversionsService
import com.valoracloud.api.facebook.AddPaymentInfoParams
import com.valoracloud.api.facebook.PurchaseParams
import com.valoracloud.api.notifications.service.NotificationsService
import com.valoracloud.api.shkeeper.SHKeeperCallback
import com.valoracloud.api.shkeeper.SHKeeperService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant

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
    private val provisioningProcessor: com.valoracloud.api.provisioning.processor.ProvisioningProcessor,
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

        // 1. Verify Stripe signature
        val event: Event = try {
            Webhook.constructEvent(rawBody, signature, webhookSecret)
        } catch (e: SignatureVerificationException) {
            log.error("Webhook signature verification failed: ${e.message}")
            throw com.valoracloud.api.common.exceptions.BadRequestException("Invalid webhook signature")
        }

        // 2. Idempotency check
        val existing = webhookEventRepo.findByStripeEventId(event.id)
        if (existing?.processed == true) {
            log.info("Event ${event.id} already processed, skipping")
            return mapOf("received" to true)
        }

        // 3. Store event
        webhookEventRepo.save(
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
            val evt = webhookEventRepo.findByStripeEventId(event.id)
            if (evt != null) {
                webhookEventRepo.save(evt.copy(processed = true))
            }
        } catch (e: Exception) {
            log.error("Error processing event ${event.id}: ${e.message}", e)
            throw e
        }

        return mapOf("received" to true)
    }

    // ─── Event handlers ─────────────────────────────────

    private fun handlePaymentSucceeded(event: Event) {
        val paymentIntent = event.dataObjectDeserializer
            .getObject()
            .orElse(null) as? PaymentIntent ?: return

        val orderId = paymentIntent.metadata["orderId"] ?: run {
            log.warn("PaymentIntent succeeded without orderId in metadata")
            return
        }

        val order = orderRepo.findById(orderId).orElse(null)
        if (order == null) {
            log.warn("Order $orderId not found")
            return
        }

        // Allow recovery: SUCCEEDED payment can reactivate FAILED order
        if (order.status !in listOf(OrderStatus.PENDING_PAYMENT, OrderStatus.FAILED)) {
            log.warn("Order $orderId already in status ${order.status}, skipping")
            return
        }

        // Update order to PAID
        orderRepo.save(order.copy(status = OrderStatus.PAID))

        // Create invoice
        invoiceRepo.save(
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

        // Notify user
        val user = userRepo.findById(order.userId).orElse(null)
        if (user != null) {
            notifications.sendPaymentConfirmationEmail(
                email = user.email,
                orderId = order.id,
                amount = order.totalAmount.toDouble(),
                language = user.language,
                userId = order.userId,
            )
        }

        // Track Facebook events (fire-and-forget)
        runCatching { facebookConversions.trackAddPaymentInfo(AddPaymentInfoParams(orderId = order.id, email = user?.email)) }
        runCatching { facebookConversions.trackPurchase(PurchaseParams(orderId = order.id, value = order.totalAmount.toDouble(), currency = "USD", email = user?.email)) }

        // Dispatch provisioning
        dispatchProvisioning(order)

        log.info("Order ${order.id} paid, provisioning job dispatched")
    }

    private fun handlePaymentFailed(event: Event) {
        val paymentIntent = event.dataObjectDeserializer
            .getObject()
            .orElse(null) as? PaymentIntent ?: return

        val orderId = paymentIntent.metadata["orderId"] ?: return

        orderRepo.findByStatus(OrderStatus.PENDING_PAYMENT.name)
            .filter { it.id == orderId }
            .forEach { orderRepo.save(it.copy(status = OrderStatus.FAILED)) }

        log.info("Order $orderId payment failed")
    }

    private fun handleChargeRefunded(event: Event) {
        // charge.refunded — find order by stripePaymentId
        val charge = event.dataObjectDeserializer
            .getObject()
            .orElse(null) as? com.stripe.model.Charge ?: return

        val paymentIntentId = charge.paymentIntent

        if (paymentIntentId.isNullOrBlank()) return

        val orders = orderRepo.findAll().filter { it.stripePaymentId == paymentIntentId }
        for (order in orders) {
            orderRepo.save(order.copy(status = OrderStatus.CANCELLED))
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
            throw com.valoracloud.api.common.exceptions.UnauthorizedException("Invalid SHKeeper API key")
        }

        val orderId = payload.externalId
        log.info("Crypto callback received for order $orderId: status=${payload.status}, paid=${payload.paid}")

        // 2. Idempotency
        val triggerTx = payload.transactions.find { it.trigger }
        val eventKey = if (triggerTx != null) "$orderId:${triggerTx.txid}" else "$orderId:${payload.status}"

        val existing = webhookEventRepo.findByExternalId(eventKey)
            .any { it.eventSource == "crypto" && it.processed }
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
        orderRepo.save(order.copy(status = OrderStatus.PAID))

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
            webhookEventRepo.save(it.copy(processed = true))
        }

        // 7. Dispatch provisioning
        dispatchProvisioning(order)

        // Track Facebook
        val cryptoUser = userRepo.findById(order.userId).orElse(null)
        runCatching { facebookConversions.trackPurchase(PurchaseParams(orderId = order.id, value = order.totalAmount.toDouble(), currency = "USD", email = cryptoUser?.email)) }

        log.info("Order $orderId crypto-paid, provisioning dispatched")
        return mapOf("received" to true)
    }

    // ─── Helpers ────────────────────────────────────────

    private fun dispatchProvisioning(order: Order) {
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
        } catch (e: Exception) {
            log.error("Queue dispatch blocked for paid order ${order.id}. Stripe/SHKeeper will retry.", e)
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
