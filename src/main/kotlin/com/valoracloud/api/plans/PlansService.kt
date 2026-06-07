package com.valoracloud.api.plans

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.valoracloud.api.common.config.ALLOWED_TERMS
import com.valoracloud.api.common.config.PlanAddon
import com.valoracloud.api.common.exceptions.BadRequestException
import com.valoracloud.api.common.exceptions.NotFoundException
import com.valoracloud.api.common.model.ProductType
import com.valoracloud.api.config.AddonCatalogRepository
import com.valoracloud.api.config.PlanRepository
import com.valoracloud.api.contabo.ContaboImage
import com.valoracloud.api.entity.AddonCatalog
import com.valoracloud.api.entity.Plan
import com.valoracloud.api.images.ImagesService
import java.math.BigDecimal
import java.math.RoundingMode
import org.springframework.stereotype.Service

@Service
class PlansService(
    private val planRepository: PlanRepository,
    private val imagesService: ImagesService,
    private val objectMapper: ObjectMapper,
    private val addonCatalogRepository: AddonCatalogRepository,
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

        val baseMonthly: BigDecimal = when (dto.billingCycle) {
            1  -> plan.price1Month
            6  -> plan.price6Months
            12 -> plan.price12Months
            else -> throw BadRequestException("billingCycle must be 1, 6, or 12")
        }
        val setupFee: BigDecimal = when (dto.billingCycle) {
            1  -> plan.setup1Month
            6  -> plan.setup6Months
            12 -> plan.setup12Months
            else -> BigDecimal.ZERO
        }

        val planAddons = (plan.availableAddons as? com.fasterxml.jackson.databind.node.ArrayNode)
            ?.map { objectMapper.treeToValue(it, PlanAddon::class.java) }
            ?: emptyList()

        val addonEntries = dto.addons.mapNotNull { addonId ->
            val planAddon = planAddons.find { it.id == addonId } ?: return@mapNotNull null
            val effectivePrice = planAddon.regionPrices[dto.region] ?: planAddon.priceMonthly
            val meta: AddonCatalog? = addonCatalogRepository.findById(addonId).orElse(null)
            QuoteAddonEntry(
                id = addonId,
                label = planAddon.label ?: meta?.label,
                priceMonthly = effectivePrice,
            )
        }

        val addonsMonthly = addonEntries.sumOf { it.priceMonthly }
        val totalMonthly = baseMonthly.toDouble() + addonsMonthly
        val subtotal = BigDecimal.valueOf(totalMonthly * dto.billingCycle)
            .setScale(2, RoundingMode.HALF_UP)
        val total = subtotal.add(setupFee).setScale(2, RoundingMode.HALF_UP)

        return QuoteResponse(
            baseMonthly = baseMonthly,
            addons = addonEntries,
            addonsMonthly = addonsMonthly,
            totalMonthly = totalMonthly,
            setupFee = setupFee,
            billingCycle = dto.billingCycle,
            subtotal = subtotal,
            total = total,
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
