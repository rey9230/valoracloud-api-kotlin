package com.valoracloud.api.orders

import com.valoracloud.api.common.dto.PaginatedResponse
import com.valoracloud.api.common.dto.PaginationDto
import com.valoracloud.api.common.exceptions.NotFoundException
import com.valoracloud.api.config.OrderRepository
import com.valoracloud.api.entity.Order
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

@Service
class OrdersService(
    private val orderRepository: OrderRepository,
    // TODO: Inject PlansService, ContaboService, EncryptionUtil, Stripe, SHKeeper, ProvisioningQueue
) {
    fun checkout(userId: String, dto: CreateOrderDto): Any {
        // TODO: Full checkout flow — validate plan, region, image addons;
        // calculate pricing; encrypt root password; create order;
        // handle Stripe PaymentIntent or crypto invoice;
        // auto-approve when Stripe disabled
        throw UnsupportedOperationException("Orders checkout not yet implemented — needs ContaboService, Stripe, SHKeeper integration")
    }

    fun findByUser(userId: String, pagination: PaginationDto): PaginatedResponse<Order> {
        val pageable = PageRequest.of(pagination.page - 1, pagination.limit)
        val page = orderRepository.findAll(pageable)
        // Filter by userId manually since we don't have a paged findByUserId
        val userOrders = page.content.filter { it.userId == userId }
        return PaginatedResponse(userOrders, userOrders.size.toLong(), pagination.page, pagination.limit, 1)
    }

    fun findOne(orderId: String, userId: String): Order {
        val order = orderRepository.findById(orderId)
            .orElseThrow { NotFoundException("Order", orderId) }
        if (order.userId != userId) throw NotFoundException("Order", orderId)
        return order
    }
}
