package com.valoracloud.api.plans

import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MarginPricingServiceTest {

    private val service = MarginPricingService(defaultMarginPercent = 30.0)

    @Test
    fun `applyMargin adds configured percent`() {
        assertEquals(BigDecimal("13.00"), service.applyMargin(BigDecimal("10.00")))
        assertEquals(BigDecimal("11.50"), service.applyMargin(BigDecimal("10.00"), BigDecimal("15")))
    }

    @Test
    fun `applyMargin returns zero for zero cost`() {
        assertEquals(BigDecimal("0.00"), service.applyMargin(BigDecimal.ZERO))
    }

    @Test
    fun `suggestPlanPrices applies term discounts after margin`() {
        val suggested = service.suggestPlanPrices(BigDecimal("10.00"))
        assertEquals(BigDecimal("13.00"), suggested.price1Month)
        assertEquals(BigDecimal("12.35"), suggested.price6Months)
        assertEquals(BigDecimal("11.70"), suggested.price12Months)
    }
}
