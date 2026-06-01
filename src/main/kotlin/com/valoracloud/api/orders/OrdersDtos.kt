package com.valoracloud.api.orders

import jakarta.validation.constraints.*

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
