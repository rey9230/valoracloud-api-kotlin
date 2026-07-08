package com.valoracloud.api.plans

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.valoracloud.api.common.exceptions.BadRequestException
import com.valoracloud.api.config.AddonCatalogRepository
import com.valoracloud.api.entity.AddonCatalog
import com.valoracloud.api.entity.Plan
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.util.Optional
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PricingServiceTest {

    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private val addonCatalogRepository: AddonCatalogRepository = mockk()
    private val marginPricingService = MarginPricingService(defaultMarginPercent = 30.0)
    private val pricingService = PricingService(objectMapper, addonCatalogRepository, marginPricingService)

    private fun samplePlan(): Plan =
        Plan(
            name = "Cloud VPS S",
            slug = "cloud-vps-s",
            price1Month = BigDecimal("10.00"),
            price6Months = BigDecimal("9.00"),
            price12Months = BigDecimal("8.00"),
            setup1Month = BigDecimal.ZERO,
            setup6Months = BigDecimal.ZERO,
            setup12Months = BigDecimal("5.00"),
            contaboPlanId = "V94",
        ).apply {
            availableAddons = objectMapper.readTree(
                """
                [
                  {"id":"region-eu","priceMonthly":0,"regionPrices":{}},
                  {"id":"region-us-east","priceMonthly":2,"regionPrices":{}},
                  {"id":"backup-auto","priceMonthly":1.5,"regionPrices":{}},
                  {"id":"networking-private","priceMonthly":2,"regionPrices":{}}
                ]
                """.trimIndent(),
            )
        }

    @Test
    fun `calculatePricing multiplies base and addons by billing cycle`() {
        every { addonCatalogRepository.findAll() } returns emptyList()
        val plan = samplePlan()
        val pricing = pricingService.calculatePricing(
            plan = plan,
            billingCycle = 12,
            regionAddonId = "region-eu",
            selectedAddonIds = listOf("backup-auto", "networking-private"),
        )

        assertEquals(BigDecimal("8.00"), pricing.baseMonthly)
        assertEquals(BigDecimal("5.00"), pricing.setupFee)
        assertEquals(3.5, pricing.addonsMonthly, 0.001)
        assertEquals(11.5, pricing.totalMonthly, 0.001)
        assertEquals(BigDecimal("138.00"), pricing.subtotal)
        assertEquals(BigDecimal("42.00"), pricing.addonsPriceForCycle)
        assertEquals(BigDecimal("143.00"), pricing.total)
    }

    @Test
    fun `calculatePricing includes region addon in billable set`() {
        every { addonCatalogRepository.findAll() } returns emptyList()
        val plan = samplePlan()
        val pricing = pricingService.calculatePricing(
            plan = plan,
            billingCycle = 1,
            regionAddonId = "region-us-east",
            selectedAddonIds = emptyList(),
        )

        assertEquals(1, pricing.addonEntries.size)
        assertEquals("region-us-east", pricing.addonEntries.first().id)
        assertEquals(BigDecimal("12.00"), pricing.total)
    }

    @Test
    fun `calculatePricing adds legacy image-cpanel from plan when label matches`() {
        every { addonCatalogRepository.findAll() } returns emptyList()

        val plan = samplePlan().apply {
            price12Months = BigDecimal("5.99")
            availableAddons = objectMapper.readTree(
                """
                [
                  {"id":"region-eu","priceMonthly":0,"regionPrices":{}},
                  {"id":"image-cpanel","priceMonthly":27.99,"regionPrices":{}}
                ]
                """.trimIndent(),
            )
        }
        val pricing = pricingService.calculatePricing(
            plan = plan,
            billingCycle = 12,
            regionAddonId = "region-eu",
            selectedAddonIds = listOf("storage-nvme-base", "backup-none", "networking-none", "objstorage-none", "monitoring-none"),
            imageId = "c0200107-cc26-4776-9775-1942841a473c",
            imageLabel = "ubuntu-24.04-cpanel",
        )

        val cpanelEntry = pricing.addonEntries.find { it.id == ImageLicenseResolver.LEGACY_CPANEL }
        assertEquals(27.99, cpanelEntry?.priceMonthly ?: 0.0, 0.001)
        assertEquals(33.98, pricing.totalMonthly, 0.001)
        assertEquals(BigDecimal("407.76"), pricing.subtotal)
    }

    @Test
    fun `calculatePricing adds cPanel license from catalog when no legacy addon`() {
        val cpanelLicense = AddonCatalog(
            id = ImageLicenseResolver.ADDON_CPANEL,
            category = "license",
            label = "cPanel License",
            contaboValue = "cPanel30",
            billingType = "monthly_recurring",
        ).apply { contaboCostPrice = BigDecimal("14.00") }
        every { addonCatalogRepository.findAll() } returns listOf(cpanelLicense)
        every { addonCatalogRepository.findById(ImageLicenseResolver.ADDON_CPANEL) } returns Optional.of(cpanelLicense)

        val plan = samplePlan()
        val pricing = pricingService.calculatePricing(
            plan = plan,
            billingCycle = 1,
            regionAddonId = "region-eu",
            selectedAddonIds = emptyList(),
            imageId = "c0200107-cc26-4776-9775-1942841a473c",
            imageLabel = "ubuntu-24.04-cpanel",
        )

        assertEquals(2, pricing.addonEntries.size)
        val licenseEntry = pricing.addonEntries.find { it.id == ImageLicenseResolver.ADDON_CPANEL }
        assertEquals(18.2, licenseEntry?.priceMonthly ?: 0.0, 0.001)
        assertEquals(28.2, pricing.totalMonthly, 0.001)
    }

    @Test
    fun `calculatePricing rejects invalid billing cycle`() {
        every { addonCatalogRepository.findAll() } returns emptyList()
        val plan = samplePlan()
        assertThrows(BadRequestException::class.java) {
            pricingService.calculatePricing(plan, 3, "region-eu", emptyList())
        }
    }

    @Test
    fun `getBaseMonthly returns term-specific monthly rate`() {
        val plan = samplePlan()
        assertEquals(BigDecimal("10.00"), pricingService.getBaseMonthly(plan, 1))
        assertEquals(BigDecimal("9.00"), pricingService.getBaseMonthly(plan, 6))
        assertEquals(BigDecimal("8.00"), pricingService.getBaseMonthly(plan, 12))
    }
}
