package com.valoracloud.api.plans

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.valoracloud.api.common.config.ALLOWED_TERMS
import com.valoracloud.api.common.config.PlanAddon
import com.valoracloud.api.common.exceptions.NotFoundException
import com.valoracloud.api.common.model.ProductType
import com.valoracloud.api.config.AddonCatalogRepository
import com.valoracloud.api.config.PlanRepository
import com.valoracloud.api.contabo.ContaboImage
import com.valoracloud.api.entity.AddonCatalog
import com.valoracloud.api.entity.Plan
import com.valoracloud.api.images.ImagesService
import java.math.BigDecimal
import org.springframework.stereotype.Service

@Service
class PlansService(
    private val planRepository: PlanRepository,
    private val imagesService: ImagesService,
    private val objectMapper: ObjectMapper,
    private val addonCatalogRepository: AddonCatalogRepository,
    private val pricingService: PricingService,
) {
    fun findAll(productType: String? = null): List<Plan> {
        return if (productType != null) {
            val type =
                    runCatching { ProductType.valueOf(productType.uppercase()) }.getOrNull()
                            ?: return emptyList()
            planRepository.findAll().filter { it.productType == type }
        } else {
            planRepository.findAll().sortedBy { it.sortOrder }
        }
    }

    fun getAllAddons(): Map<String, Any> {
        val addons = addonCatalogRepository.findAllByOrderBySortOrderAsc()
        return mapOf("addons" to addons, "billingTerms" to ALLOWED_TERMS)
    }

    fun findById(id: String): Map<String, Any> {
        val plan = planRepository.findById(id).orElseThrow { NotFoundException("Plan", id) }
        val planMap = objectMapper.convertValue(plan, object : TypeReference<MutableMap<String, Any>>() {})

        @Suppress("UNCHECKED_CAST")
        val dynamicImageAddons = (imagesService.listAllImages(standardOnly = true) as? List<ContaboImage>)
            ?.map { image ->
                mapOf(
                    "id" to image.imageId,
                    "category" to "image",
                    "label" to image.name,
                    "contaboValue" to image.imageId,
                    "billingType" to "monthly_recurring",
                )
            } ?: emptyList()

        val availableAddonsFromPlan = (plan.availableAddons as? com.fasterxml.jackson.databind.node.ArrayNode)
            ?.map { objectMapper.treeToValue(it, PlanAddon::class.java) }
            ?: emptyList()

        val enrichedAddons = availableAddonsFromPlan.map { planAddon ->
            val meta: AddonCatalog? = addonCatalogRepository.findById(planAddon.id).orElse(null)
            mapOf(
                "id" to planAddon.id,
                "priceMonthly" to planAddon.priceMonthly,
                "regionPrices" to planAddon.regionPrices,
                "label" to (planAddon.label ?: meta?.label),
                "category" to meta?.category,
                "billingType" to (meta?.billingType ?: "monthly_recurring"),
            )
        }

        planMap["availableAddons"] = enrichedAddons
        planMap["imageAddons"] = dynamicImageAddons
        planMap.remove("addons")

        return planMap
    }

    fun findBySlug(slug: String): Plan {
        return planRepository.findBySlug(slug) ?: throw NotFoundException("Plan", slug)
    }

    fun findByContaboPlanId(contaboPlanId: String): Plan? {
        return planRepository.findAll().find { it.contaboPlanId == contaboPlanId }
    }

    fun calculateQuote(planId: String, dto: QuoteRequestDto): QuoteResponse {
        val plan = planRepository.findById(planId)
            .orElseThrow { NotFoundException("Plan", planId) }

        val pricing = pricingService.calculatePricing(
            plan = plan,
            billingCycle = dto.billingCycle,
            regionAddonId = dto.region,
            selectedAddonIds = dto.addons,
        )

        val planAddons = pricingService.parsePlanAddons(plan)
        val enrichedAddons = pricing.addonEntries.map { entry ->
            val meta: AddonCatalog? = addonCatalogRepository.findById(entry.id).orElse(null)
            val planAddon = planAddons.find { it.id == entry.id }
            entry.copy(label = entry.label ?: planAddon?.label ?: meta?.label)
        }

        return QuoteResponse(
            baseMonthly = pricing.baseMonthly,
            addons = enrichedAddons,
            addonsMonthly = pricing.addonsMonthly,
            totalMonthly = pricing.totalMonthly,
            setupFee = pricing.setupFee,
            billingCycle = dto.billingCycle,
            subtotal = pricing.subtotal,
            total = pricing.total,
        )
    }
}

data class QuoteRequestDto(
    val region: String,
    val addons: List<String> = emptyList(),
    val billingCycle: Int,
    val imageId: String? = null,
)

data class QuoteAddonEntry(
    val id: String,
    val label: String?,
    val priceMonthly: Double,
)

data class QuoteResponse(
    val baseMonthly: BigDecimal,
    val addons: List<QuoteAddonEntry>,
    val addonsMonthly: Double,
    val totalMonthly: Double,
    val setupFee: BigDecimal,
    val billingCycle: Int,
    val subtotal: BigDecimal,
    val total: BigDecimal,
    val currency: String = "USD",
)
