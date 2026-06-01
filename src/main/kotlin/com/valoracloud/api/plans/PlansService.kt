package com.valoracloud.api.plans

import com.valoracloud.api.common.exceptions.NotFoundException
import com.valoracloud.api.config.PlanRepository
import com.valoracloud.api.entity.Plan
import org.springframework.stereotype.Service

@Service
class PlansService(
    private val planRepository: PlanRepository,
) {
    fun findAll(productType: String? = null): List<Plan> {
        return if (productType != null) {
            planRepository.findByProductTypeAndStatus(productType, "ACTIVE")
        } else {
            planRepository.findByStatusOrderBySortOrderAsc("ACTIVE")
        }
    }

    fun findById(id: String): Plan {
        return planRepository.findById(id)
            .orElseThrow { NotFoundException("Plan", id) }
    }

    fun findBySlug(slug: String): Plan {
        return planRepository.findBySlug(slug)
            ?: throw NotFoundException("Plan", slug)
    }

    fun findByContaboPlanId(contaboPlanId: String): Plan? {
        return planRepository.findAll().find { it.contaboPlanId == contaboPlanId }
    }
}
