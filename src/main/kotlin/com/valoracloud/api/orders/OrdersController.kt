package com.valoracloud.api.orders

import com.valoracloud.api.auth.security.CurrentUser
import com.valoracloud.api.common.dto.PaginationDto
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/orders")
class OrdersController(
    private val ordersService: OrdersService,
) {
    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    fun checkout(
        @CurrentUser userId: String,
        @Valid @RequestBody dto: CreateOrderDto,
    ) = ordersService.checkout(userId, dto)

    @GetMapping
    fun findAll(
        @CurrentUser userId: String,
        @Valid pagination: PaginationDto,
    ) = ordersService.findByUser(userId, pagination)

    @GetMapping("/{id}")
    fun findOne(
        @PathVariable id: String,
        @CurrentUser userId: String,
    ) = ordersService.findOne(id, userId)
}
