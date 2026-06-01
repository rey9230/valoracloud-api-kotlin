package com.valoracloud.api.users

import jakarta.validation.constraints.Size

data class UpdateUserDto(
    @field:Size(min = 1, max = 50)
    val firstName: String? = null,

    @field:Size(min = 1, max = 50)
    val lastName: String? = null,

    val language: String? = null,
)
