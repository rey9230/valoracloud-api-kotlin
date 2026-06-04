package com.valoracloud.api.orders

import com.fasterxml.jackson.databind.ObjectMapper
import com.stripe.Stripe
import com.stripe.model.PaymentIntent
import com.stripe.param.PaymentIntentCreateParams
import com.valoracloud.api.billing.service.ProvisionJobData
import com.valoracloud.api.common.config.PlanAddon
import com.valoracloud.api.common.dto.PaginatedResponse
import com.valoracloud.api.common.dto.PaginationDto
import com.valoracloud.api.common.exceptions.BadRequestException
import com.valoracloud.api.common.exceptions.NotFoundException
import com.valoracloud.api.common.model.OrderStatus
import com.valoracloud.api.common.model.PaymentMethod
import com.valoracloud.api.common.model.ServiceType
import com.valoracloud.api.common.utils.EncryptionUtil
import com.valoracloud.api.config.OrderRepository
import com.valoracloud.api.config.PlanRepository
import com.valoracloud.api.entity.Order
import com.valoracloud.api.provisioning.processor.ProvisioningProcessor
import java.math.BigDecimal
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OrdersService(
    private val orderRepository: OrderRepository,
    private val planRepository: PlanRepository,
    private val provisioningProcessor: ProvisioningProcessor,
    private val objectMapper: ObjectMapper,
    @Value("\${stripe.enabled:true}") private val stripeEnabled: Boolean,
    @Value("\${stripe.secret-key:}") private val stripeSecretKey: String,
    @Value("\${app.encryption-key:}") private val encryptionKey: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    init {
        if (stripeSecretKey.isNotBlank() && stripeEnabled) Stripe.apiKey = stripeSecretKey
    }

    @Transactional
    fun checkout(userId: String, dto: CreateOrderDto): CheckoutResponse {
        val plan = planRepository.findById(dto.planId)
            .orElseThrow { NotFoundException("Plan", dto.planId) }

        // 1. Calculate Base Price
        val basePrice: BigDecimal = when (dto.billingCycle) {
            1  -> plan.price1Month
            6  -> plan.price6Months
            12 -> plan.price12Months
            else -> throw BadRequestException("billingCycle must be 1, 6, or 12")
        }

        // 2. Calculate Setup Fee
        val setupFee: BigDecimal = when (dto.billingCycle) {
            1  -> plan.setup1Month
            6  -> plan.setup6Months
            12 -> plan.setup12Months
            else -> BigDecimal.ZERO
        }

        // 3. Calculate Addons Price
        var addonsPrice = BigDecimal.ZERO
        if (dto.addons.isNotEmpty()) {
            val availableAddons = (plan.availableAddons as? com.fasterxml.jackson.databind.node.ArrayNode)
                ?.map { objectMapper.treeToValue(it, PlanAddon::class.java) }
                ?: emptyList()

            for (addonId in dto.addons) {
                val addonConfig = availableAddons.find { it.id == addonId }
                if (addonConfig != null) {
                    // Multiply addon monthly price by billing cycle months
                    val monthlyPrice = BigDecimal.valueOf(addonConfig.priceMonthly)
                    val cyclePrice = monthlyPrice.multiply(BigDecimal(dto.billingCycle))
                    addonsPrice = addonsPrice.add(cyclePrice)
                }
            }
        }

        // 4. Calculate Final Total Amount
        val totalAmount = basePrice.add(setupFee).add(addonsPrice)

        val storedPassword = if (encryptionKey.isNotBlank())
            EncryptionUtil.encrypt(dto.rootPassword, encryptionKey)
        else dto.rootPassword

        val order = orderRepository.save(Order(
            userId = userId,
            planId = plan.id,
            serviceType = ServiceType.COMPUTE,
            status = OrderStatus.PENDING_PAYMENT,
            paymentMethod = if (!stripeEnabled) PaymentMethod.STRIPE else PaymentMethod.STRIPE,
            billingCycle = dto.billingCycle,
            basePrice = basePrice,
            setupFee = setupFee,
            addonsPrice = addonsPrice, // Save calculated addons price
            totalAmount = totalAmount,
            region = dto.region,
            os = dto.imageId ?: "",
            addons = dto.addons,
            rootPassword = storedPassword,
            hostname = dto.displayName,
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

        // Create Stripe PaymentIntent
        val amountCents = totalAmount.multiply(BigDecimal(100)).toLong()
        val intent = PaymentIntent.create(
            PaymentIntentCreateParams.builder()
                .setAmount(amountCents)
                .setCurrency("usd")
                .putMetadata("orderId", order.id)
                .putMetadata("userId", userId)
                .build()
        )
        order.stripePaymentId = intent.id
        orderRepository.save(order)

        return CheckoutResponse(
            orderId = order.id,
            clientSecret = intent.clientSecret,
            status = "pending_payment",
        )
    }

    fun findByUser(userId: String, pagination: PaginationDto): PaginatedResponse<Order> {
        val pageable = PageRequest.of(pagination.page - 1, pagination.limit)
        val page = orderRepository.findAll(pageable)
        // Filter by userId manually since we don't have a paged findByUserId
        val userOrders = page.content.filter { it.userId == userId }
        return PaginatedResponse(userOrders, userOrders.size.toLong(), pagination.page, pagination.limit, 1)
    }

    fun findOne(orderId: String, userId: String): Order {
        val order = orderRepository.findById(orderId)
            .orElseThrow { NotFoundException("Order", orderId) }
        if (order.userId != userId) throw NotFoundException("Order", orderId)
        return order
    }
}

data class CheckoutResponse(
    val orderId: String,
    val status: String,
    val clientSecret: String? = null,
)