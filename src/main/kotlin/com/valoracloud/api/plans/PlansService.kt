package com.valoracloud.api.plans

import com.fasterxml.jackson.databind.ObjectMapper
import com.valoracloud.api.common.config.ALL_ADDONS
import com.valoracloud.api.common.config.AddonCategory
import com.valoracloud.api.common.config.AddonMeta
import com.valoracloud.api.common.config.PlanAddon
import com.valoracloud.api.common.exceptions.NotFoundException
import com.valoracloud.api.common.model.ProductType
import com.valoracloud.api.config.PlanRepository
import com.valoracloud.api.contabo.ContaboImage
import com.valoracloud.api.entity.Plan
import com.valoracloud.api.images.ImagesService
import org.springframework.stereotype.Service

@Service
class PlansService(
    private val planRepository: PlanRepository,
    private val imagesService: ImagesService,
    private val objectMapper: ObjectMapper,
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

    fun findById(id: String): Map<String, Any> {
        val plan = planRepository.findById(id).orElseThrow { NotFoundException("Plan", id) }
        val planMap = objectMapper.convertValue(plan, MutableMap::class.java) as MutableMap<String, Any>

        val dynamicImageAddons = (imagesService.listAllImages(standardOnly = true) as? List<ContaboImage>)
            ?.map { image ->
                AddonMeta(
                    id = image.imageId,
                    category = AddonCategory.IMAGE,
                    label = image.name,
                    contaboValue = image.imageId
                )
            } ?: emptyList()

        val availableAddonsFromPlan = (plan.availableAddons as? com.fasterxml.jackson.databind.node.ArrayNode)
            ?.map { objectMapper.treeToValue(it, PlanAddon::class.java) }
            ?: emptyList()

        val enrichedAddons = availableAddonsFromPlan.map { planAddon ->
            val meta = ALL_ADDONS.find { it.id == planAddon.id }
            mapOf(
                "id" to planAddon.id,
                "priceMonthly" to planAddon.priceMonthly,
                "label" to (planAddon.label ?: meta?.label),
                "category" to (meta?.category?.id)
            )
        }

        planMap["availableAddons"] = enrichedAddons
        planMap["imageAddons"] = dynamicImageAddons
        planMap.remove("addons") // Remove the raw JSON field

        return planMap
    }

    fun findBySlug(slug: String): Plan {
        return planRepository.findBySlug(slug) ?: throw NotFoundException("Plan", slug)
    }

    fun findByContaboPlanId(contaboPlanId: String): Plan? {
        return planRepository.findAll().find { it.contaboPlanId == contaboPlanId }
    }
}