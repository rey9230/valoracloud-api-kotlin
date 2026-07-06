package com.valoracloud.api.plans

import java.math.BigDecimal
import java.math.RoundingMode
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class MarginPricingService(
    @Value("\${app.pricing.default-margin-percent:30}") private val defaultMarginPercent: Double,
) {
    fun defaultMarginPercent(): BigDecimal =
        BigDecimal.valueOf(defaultMarginPercent).setScale(2, RoundingMode.HALF_UP)

    fun applyMargin(cost: BigDecimal, marginPercent: BigDecimal? = null): BigDecimal {
        if (cost <= BigDecimal.ZERO) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        val margin = marginPercent ?: defaultMarginPercent()
        val multiplier = BigDecimal.ONE.add(margin.divide(BigDecimal(100), 6, RoundingMode.HALF_UP))
        return cost.multiply(multiplier).setScale(2, RoundingMode.HALF_UP)
    }

    data class SuggestedPlanPrices(
        val price1Month: BigDecimal,
        val price6Months: BigDecimal,
        val price12Months: BigDecimal,
    )

    /**
     * Suggests retail prices from Contabo wholesale cost.
     * 6/12 month tiers apply modest commitment discounts on top of margin.
     */
    fun suggestPlanPrices(
        contaboCostMonthly: BigDecimal,
        marginPercent: BigDecimal? = null,
    ): SuggestedPlanPrices {
        val retail1 = applyMargin(contaboCostMonthly, marginPercent)
        return SuggestedPlanPrices(
            price1Month = retail1,
            price6Months = retail1.multiply(BigDecimal("0.95")).setScale(2, RoundingMode.HALF_UP),
            price12Months = retail1.multiply(BigDecimal("0.90")).setScale(2, RoundingMode.HALF_UP),
        )
    }

    fun suggestAddonPrice(
        contaboCostMonthly: BigDecimal,
        marginPercent: BigDecimal? = null,
    ): BigDecimal = applyMargin(contaboCostMonthly, marginPercent)
}
