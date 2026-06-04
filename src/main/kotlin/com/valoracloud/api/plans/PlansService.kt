package com.valoracloud.api.plans

import com.fasterxml.jackson.databind.ObjectMapper
import com.valoracloud.api.common.config.AddonCategory
import com.valoracloud.api.common.config.AddonMeta
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

        val availableAddons = (plan.availableAddons as? com.fasterxml.jackson.databind.node.ArrayNode)
            ?.map { objectMapper.treeToValue(it, com.valoracloud.api.common.config.PlanAddon::class.java) }
            ?: emptyList()

        planMap["availableAddons"] = availableAddons
        planMap["imageAddons"] = dynamicImageAddons

        return planMap
    }

    fun findBySlug(slug: String): Plan {
        return planRepository.findBySlug(slug) ?: throw NotFoundException("Plan", slug)
    }

    fun findByContaboPlanId(contaboPlanId: String): Plan? {
        return planRepository.findAll().find { it.contaboPlanId == contaboPlanId }
    }
}