package com.valoracloud.api.plans

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.valoracloud.api.common.config.PlanAddon
import com.valoracloud.api.common.exceptions.BadRequestException
import com.valoracloud.api.entity.Plan
import java.math.BigDecimal
import java.math.RoundingMode
import org.springframework.stereotype.Service

@Service
class PricingService(
    private val objectMapper: ObjectMapper,
) {
    fun parsePlanAddons(plan: Plan): List<PlanAddon> =
        (plan.availableAddons as? ArrayNode)
            ?.map { objectMapper.treeToValue(it, PlanAddon::class.java) }
            ?: emptyList()

    fun getBaseMonthly(plan: Plan, billingCycle: Int): BigDecimal =
        when (billingCycle) {
            1 -> plan.price1Month
            6 -> plan.price6Months
            12 -> plan.price12Months
            else -> throw BadRequestException("billingCycle must be 1, 6, or 12")
        }

    fun getSetupFee(plan: Plan, billingCycle: Int): BigDecimal =
        when (billingCycle) {
            1 -> plan.setup1Month
            6 -> plan.setup6Months
            12 -> plan.setup12Months
            else -> BigDecimal.ZERO
        }

    /**
     * Calculates order/quote pricing. [regionAddonId] is the addon catalog id (e.g. region-eu).
     * [selectedAddonIds] should exclude the region id if it is passed separately via [regionAddonId].
     */
    fun calculatePricing(
        plan: Plan,
        billingCycle: Int,
        regionAddonId: String,
        selectedAddonIds: List<String>,
    ): OrderPricing {
        val baseMonthly = getBaseMonthly(plan, billingCycle)
        val setupFee = getSetupFee(plan, billingCycle)
        val planAddons = parsePlanAddons(plan)

        val billableAddonIds = buildBillableAddonIds(regionAddonId, selectedAddonIds)
        val addonEntries = billableAddonIds.mapNotNull { addonId ->
            val planAddon = planAddons.find { it.id == addonId } ?: return@mapNotNull null
            val effectivePrice = planAddon.regionPrices[regionAddonId] ?: planAddon.priceMonthly
            QuoteAddonEntry(
                id = addonId,
                label = planAddon.label,
                priceMonthly = effectivePrice,
            )
        }

        val addonsMonthly = addonEntries.sumOf { it.priceMonthly }
        val totalMonthly = baseMonthly.toDouble() + addonsMonthly
        val subtotal = BigDecimal.valueOf(totalMonthly * billingCycle)
            .setScale(2, RoundingMode.HALF_UP)
        val addonsPriceForCycle = BigDecimal.valueOf(addonsMonthly * billingCycle)
            .setScale(2, RoundingMode.HALF_UP)
        val total = subtotal.add(setupFee).setScale(2, RoundingMode.HALF_UP)

        return OrderPricing(
            baseMonthly = baseMonthly,
            setupFee = setupFee,
            addonEntries = addonEntries,
            addonsMonthly = addonsMonthly,
            totalMonthly = totalMonthly,
            subtotal = subtotal,
            total = total,
            addonsPriceForCycle = addonsPriceForCycle,
        )
    }

    private fun buildBillableAddonIds(regionAddonId: String, selectedAddonIds: List<String>): List<String> {
        val ids = linkedSetOf<String>()
        if (regionAddonId.isNotBlank()) ids.add(regionAddonId)
        selectedAddonIds.filter { it.isNotBlank() && it != regionAddonId }.forEach { ids.add(it) }
        return ids.toList()
    }
}

data class OrderPricing(
    val baseMonthly: BigDecimal,
    val setupFee: BigDecimal,
    val addonEntries: List<QuoteAddonEntry>,
    val addonsMonthly: Double,
    val totalMonthly: Double,
    val subtotal: BigDecimal,
    val total: BigDecimal,
    val addonsPriceForCycle: BigDecimal,
)
