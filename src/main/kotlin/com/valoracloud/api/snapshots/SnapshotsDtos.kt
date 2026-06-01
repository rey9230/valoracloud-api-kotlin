package com.valoracloud.api.snapshots

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateSnapshotDto(
        @field:NotBlank @field:Size(min = 1, max = 30) val name: String,
        @field:Size(max = 255) val description: String? = null,
)

data class UpdateSnapshotDto(
        @field:Size(min = 1, max = 30) val name: String? = null,
        @field:Size(max = 255) val description: String? = null,
)
