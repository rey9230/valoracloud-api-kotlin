package com.valoracloud.api.objectstorage

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

data class CreateObjectStorageDto(
    @field:NotBlank
    val region: String = "EU",

    @field:Min(0)
    val totalPurchasedSpaceTB: Double = 0.25,

    val displayName: String? = null,

    val autoScaling: AutoScalingConfig? = null,
)

data class AutoScalingConfig(
    val state: String = "enabled", // enabled | disabled
    val sizeLimitTB: Double,
)

data class UpgradeObjectStorageDto(
    @field:Min(0)
    val totalPurchasedSpaceTB: Double? = null,

    val autoScaling: AutoScalingConfig? = null,
)
