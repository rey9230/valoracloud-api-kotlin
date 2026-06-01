package com.valoracloud.api.secrets

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateSecretDto(
    @field:NotBlank
    @field:Size(min = 1, max = 255)
    val name: String,

    @field:NotBlank
    val type: String, // "ssh" | "password"

    @field:NotBlank
    @field:Size(min = 1)
    val value: String,
)

data class UpdateSecretDto(
    @field:Size(min = 1, max = 255)
    val name: String? = null,

    @field:Size(min = 1)
    val value: String? = null,
)
