package com.valoracloud.api.tags

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class CreateTagDto(
    @field:NotBlank
    @field:Size(min = 1, max = 255)
    val name: String,

    @field:NotBlank
    @field:Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "color must be a valid hex color (e.g. #ff0000)")
    val color: String,
)

data class UpdateTagDto(
    @field:Size(min = 1, max = 255)
    val name: String? = null,

    @field:Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "color must be a valid hex color (e.g. #ff0000)")
    val color: String? = null,
)

data class CreateTagAssignmentDto(
    @field:NotBlank
    val resourceType: String, // instance, image, object-storage, snapshot, private-network, firewall, vip

    @field:NotBlank
    val resourceId: String,
)
