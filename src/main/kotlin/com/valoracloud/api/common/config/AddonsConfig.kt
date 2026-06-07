package com.valoracloud.api.common.config

/**
 * Addon pricing data as stored in plan.availableAddons JSONB.
 * Metadata (label, category, billingType) lives in the addon_catalog table.
 */
data class PlanAddon(
    val id: String,
    val priceMonthly: Double,
    val label: String? = null,
    val regionPrices: Map<String, Double> = emptyMap(),
)

val ALLOWED_TERMS = listOf(1, 6, 12)

fun findPlanAddonPrice(
    planAddons: List<PlanAddon>,
    addonId: String,
    regionId: String? = null,
): Double? {
    val entry = planAddons.find { it.id == addonId } ?: return null
    if (regionId != null) entry.regionPrices[regionId]?.let { return it }
    return entry.priceMonthly
}

fun calculateAddonsCost(
    planAddons: List<PlanAddon>,
    selectedAddonIds: List<String>,
    regionId: String? = null,
): Double = selectedAddonIds.sumOf { id -> findPlanAddonPrice(planAddons, id, regionId) ?: 0.0 }
