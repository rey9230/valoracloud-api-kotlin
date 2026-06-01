package com.valoracloud.api.images

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateCustomImageDto(
    @field:NotBlank
    @field:Size(max = 255)
    val name: String,

    @field:Size(max = 500)
    val description: String? = null,

    @field:NotBlank
    val url: String, // URL to .qcow2 or .iso file

    @field:NotBlank
    val osType: String, // "Windows" | "Linux"

    @field:NotBlank
    @field:Size(max = 50)
    val version: String, // e.g. "20.04", "Server 2019"
)

data class UpdateImageDto(
    @field:Size(max = 255)
    val name: String? = null,

    @field:Size(max = 500)
    val description: String? = null,
)
