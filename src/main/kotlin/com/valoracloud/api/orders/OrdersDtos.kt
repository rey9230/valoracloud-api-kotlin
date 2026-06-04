package com.valoracloud.api.orders

import jakarta.validation.constraints.*
import java.math.BigDecimal

data class OrderResponseDto(
    val id: String,
    val userId: String,
    val planId: String?,
    val planName: String?,
    val serviceType: String,
    val status: String,
    val paymentMethod: String,
    val stripePaymentId: String?,
    val billingCycle: Int,
    val basePrice: BigDecimal,
    val addonsPrice: BigDecimal,
    val setupFee: BigDecimal,
    val totalAmount: BigDecimal,
    val region: String,
    val os: String,
    val addons: List<String>,
    val sshUser: String,
    val hostname: String?,
    val rootPassword: String?,
    val domainName: String?,
    val createdAt: String,
    val updatedAt: String,
)

data class CreateOrderDto(
    @field:NotBlank
    val planId: String,

    @field:Min(1)
    val billingCycle: Int,

    @field:NotBlank
    val region: String,

    @field:NotBlank
    val imageId: String,

    @field:Size(max = 10)
    val addons: List<String> = emptyList(),

    @field:NotBlank
    @field:Size(min = 8, max = 72)
    val rootPassword: String,

    @field:Size(max = 64)
    val displayName: String? = null,

    val paymentMethod: String? = null, // "stripe" or "crypto"

    val cryptoCurrency: String? = null,
)