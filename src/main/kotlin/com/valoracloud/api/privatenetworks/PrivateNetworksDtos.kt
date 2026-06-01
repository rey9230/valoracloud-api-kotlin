package com.valoracloud.api.privatenetworks

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreatePrivateNetworkDto(
    @field:NotBlank
    @field:Size(min = 1, max = 255)
    val name: String,

    @field:Size(max = 255)
    val description: String? = null,

    val region: String? = null, // Defaults to "EU"
)

data class UpdatePrivateNetworkDto(
    @field:Size(min = 1, max = 255)
    val name: String? = null,

    @field:Size(max = 255)
    val description: String? = null,
)
