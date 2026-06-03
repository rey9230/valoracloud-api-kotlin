package com.valoracloud.api.provisioning.service

import com.valoracloud.api.common.model.OrderStatus
import com.valoracloud.api.config.OrderRepository
import com.valoracloud.api.entity.Order
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Handles the few DB operations in ProvisioningProcessor that MUST be atomic.
 *
 * ProvisioningProcessor cannot use @Transactional for its main methods because:
 * - They run for 15-25 minutes (polling + SSH), which would hold a DB connection the entire time.
 * - Self-calls within a Spring bean bypass the AOP proxy, so @Transactional has no effect anyway.
 *
 * By extracting the critical atomic operations here (separate bean → proxy applies),
 * we get proper isolation without holding long-running transactions.
 */
@Service
class ProvisioningTxHelper(
    private val orderRepo: OrderRepository,
) {
    /**
     * Atomically claims the order: PAID → PROVISIONING.
     *
     * This is the only truly atomic operation needed: we must check that the order
     * is still PAID (not already claimed by a Stripe retry or another job) and
     * atomically transition it to PROVISIONING in a single transaction.
     *
     * Returns the updated Order if claimed, null if it cannot be claimed
     * (not found, already in progress/active/failed/cancelled, or not PAID).
     */
    @Transactional
    fun claimOrder(orderId: String): Order? {
        val order = orderRepo.findById(orderId).orElse(null) ?: return null
        if (order.status in setOf(
                OrderStatus.CANCELLED,
                OrderStatus.FAILED,
                OrderStatus.PROVISIONING,
                OrderStatus.ACTIVE,
        )) return null
        if (order.status != OrderStatus.PAID) return null
        order.status = OrderStatus.PROVISIONING
        return orderRepo.save(order)
    }
}

