package com.valoracloud.api.servers

import jakarta.validation.constraints.Size

data class ChangePasswordDto(
    @field:NotBlank
    @field:Size(min = 8, max = 128)
    val password: String,
)

data class ReinstallDto(
    @field:NotBlank
    val imageId: String,

    val password: String? = null,
)
