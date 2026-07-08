package com.valoracloud.api.orders

import com.stripe.Stripe
import com.stripe.model.checkout.Session
import com.stripe.param.checkout.SessionCreateParams
import com.valoracloud.api.billing.service.ProvisionJobData
import com.valoracloud.api.common.dto.PaginatedResponse
import com.valoracloud.api.common.dto.PaginationDto
import com.valoracloud.api.common.exceptions.NotFoundException
import com.valoracloud.api.common.model.OrderStatus
import com.valoracloud.api.common.model.PaymentMethod
import com.valoracloud.api.common.model.ServiceType
import com.valoracloud.api.common.utils.EncryptionUtil
import com.valoracloud.api.config.DomainRepository
import com.valoracloud.api.config.OrderRepository
import com.valoracloud.api.config.PlanRepository
import com.valoracloud.api.entity.Order
import com.valoracloud.api.plans.PricingService
import com.valoracloud.api.provisioning.processor.ProvisioningProcessor
import java.math.BigDecimal
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OrdersService(
    private val orderRepository: OrderRepository,
    private val planRepository: PlanRepository,
    private val domainRepository: DomainRepository,
    private val provisioningProcessor: ProvisioningProcessor,
    private val pricingService: PricingService,
    private val credentialsResolver: OrderCredentialsResolver,
    @Value("\${stripe.enabled:true}") private val stripeEnabled: Boolean,
    @Value("\${stripe.secret-key:}") private val stripeSecretKey: String,
    @Value("\${app.encryption-key:}") private val encryptionKey: String,
    @Value("\${app.frontend-url:}") private val frontendUrl: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    init {
        if (stripeSecretKey.isNotBlank() && stripeEnabled) Stripe.apiKey = stripeSecretKey
    }

    @Transactional
    fun checkout(userId: String, dto: CreateOrderDto): CheckoutResponse {
        val plan = planRepository.findById(dto.planId)
            .orElseThrow { NotFoundException("Plan", dto.planId) }

        val pricing = pricingService.calculatePricing(
            plan = plan,
            billingCycle = dto.billingCycle,
            regionAddonId = dto.region,
            selectedAddonIds = dto.addons,
            imageId = dto.imageId,
            imageLabel = dto.imageLabel,
        )

        val credentials = credentialsResolver.resolve(userId, dto)

        val storedPassword = if (encryptionKey.isNotBlank())
            EncryptionUtil.encrypt(credentials.rootPassword, encryptionKey)
        else credentials.rootPassword

        val imageId = dto.imageId ?: ""
        val isWindows = imageId.contains("windows", ignoreCase = true)
        val sshUser = if (isWindows) "administrator" else "admin"

        val order = orderRepository.save(Order(
            userId = userId,
            planId = plan.id,
            serviceType = ServiceType.COMPUTE,
            status = OrderStatus.PENDING_PAYMENT,
            paymentMethod = if (!stripeEnabled) PaymentMethod.STRIPE else PaymentMethod.STRIPE,
            billingCycle = dto.billingCycle,
            basePrice = pricing.baseMonthly,
            setupFee = pricing.setupFee,
            addonsPrice = pricing.addonsPriceForCycle,
            totalAmount = pricing.total,
            region = dto.region,
            os = imageId,
            addons = pricing.addonEntries.map { it.id },
            rootPassword = storedPassword,
            hostname = dto.displayName,
            sshUser = sshUser,
            sshKeyId = credentials.sshKeyId,
        ))

        // When Stripe is disabled, auto-approve and provision immediately
        if (!stripeEnabled) {
            order.status = OrderStatus.PAID
            orderRepository.save(order)
            try {
                provisioningProcessor.provisionServer(ProvisionJobData(
                    orderId = order.id,
                    planId = plan.id,
                    userId = userId,
                    region = dto.region,
                    os = dto.imageId ?: "",
                    hostname = dto.displayName,
                ))
            } catch (e: Exception) {
                log.error("Auto-provisioning failed for order ${order.id}", e)
            }
            return CheckoutResponse(orderId = order.id, status = "auto_approved")
        }

        // Create a Stripe Checkout Session (hosted payment page).
        // orderId/userId are attached to the underlying PaymentIntent so the
        // existing `payment_intent.succeeded` webhook keeps working unchanged.
        val amountCents = pricing.total.multiply(BigDecimal(100)).toLong()
        val baseUrl = frontendUrl.trimEnd('/').ifBlank { "https://valoracloud.com" }
        val session = Session.create(
            SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl("$baseUrl/checkout/success?order=${order.id}")
                .setCancelUrl("$baseUrl/checkout/cancel?order=${order.id}")
                .putMetadata("orderId", order.id)
                .putMetadata("userId", userId)
                .setPaymentIntentData(
                    SessionCreateParams.PaymentIntentData.builder()
                        .putMetadata("orderId", order.id)
                        .putMetadata("userId", userId)
                        .build()
                )
                .addLineItem(
                    SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(
                            SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency("usd")
                                .setUnitAmount(amountCents)
                                .setProductData(
                                    SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(plan.name.ifBlank { "VALORA Cloud order" })
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .build()
        )
        order.stripePaymentId = session.paymentIntent ?: session.id
        orderRepository.save(order)

        return CheckoutResponse(
            orderId = order.id,
            clientSecret = null,
            checkoutUrl = session.url,
            status = "pending_payment",
        )
    }

    fun findByUser(userId: String, pagination: PaginationDto): PaginatedResponse<OrderResponseDto> {
        val allOrders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId)
        val total = allOrders.size.toLong()
        val from = pagination.offset.coerceAtMost(allOrders.size)
        val to = (from + pagination.limit).coerceAtMost(allOrders.size)
        val page = allOrders.subList(from, to)

        val planIds = page.mapNotNull { it.planId }.distinct()
        val planMap = planRepository.findAllById(planIds).associateBy { it.id }

        val domainOrderIds = page.filter { it.serviceType == ServiceType.DOMAIN }.map { it.id }
        val domainMap = domainRepository.findByOrderIdIn(domainOrderIds).associateBy { it.orderId }

        return PaginatedResponse(
            data = page.map { it.toDto(planMap[it.planId], domainMap[it.id]) },
            total = total,
            page = pagination.page,
            limit = pagination.limit,
            totalPages = ((total + pagination.limit - 1) / pagination.limit).toInt(),
        )
    }

    fun findOne(orderId: String, userId: String): OrderResponseDto {
        val order = orderRepository.findById(orderId)
            .orElseThrow { NotFoundException("Order", orderId) }
        if (order.userId != userId) throw NotFoundException("Order", orderId)
        val plan = order.planId?.let { planRepository.findById(it).orElse(null) }
        val domain = if (order.serviceType == ServiceType.DOMAIN)
            domainRepository.findByOrderId(order.id) else null
        return order.toDto(plan, domain)
    }

    private fun Order.toDto(
        plan: com.valoracloud.api.entity.Plan?,
        domain: com.valoracloud.api.entity.Domain?,
    ) = OrderResponseDto(
        id = id,
        userId = userId,
        planId = planId,
        planName = plan?.name,
        serviceType = serviceType.name,
        status = status.name,
        paymentMethod = paymentMethod.name,
        stripePaymentId = stripePaymentId,
        billingCycle = billingCycle,
        basePrice = basePrice,
        addonsPrice = addonsPrice,
        setupFee = setupFee,
        totalAmount = totalAmount,
        region = region,
        os = os,
        addons = addons,
        sshUser = sshUser,
        sshKeyId = sshKeyId,
        hostname = hostname,
        rootPassword = rootPassword,
        domainName = domain?.domainName,
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString(),
    )
}

data class CheckoutResponse(
    val orderId: String,
    val status: String,
    val clientSecret: String? = null,
    val checkoutUrl: String? = null,
)