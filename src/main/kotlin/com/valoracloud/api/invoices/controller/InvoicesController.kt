package com.valoracloud.api.invoices.controller

import com.valoracloud.api.auth.security.CurrentUser
import com.valoracloud.api.common.dto.PaginationDto
import com.valoracloud.api.common.dto.PaginatedResponse
import com.valoracloud.api.invoices.service.InvoiceDetailDto
import com.valoracloud.api.invoices.service.InvoiceDto
import com.valoracloud.api.invoices.service.InvoicesService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/invoices")
@Tag(name = "Invoices")
@SecurityRequirement(name = "bearerAuth")
class InvoicesController(
    private val invoicesService: InvoicesService,
) {

    @GetMapping
    @Operation(summary = "List all invoices for the authenticated user")
    @ApiResponse(responseCode = "200", description = "Paginated list of invoices (Stripe + Crypto)")
    fun findAll(
        @CurrentUser userId: String,
        @ModelAttribute pagination: PaginationDto,
    ): PaginatedResponse<InvoiceDto> = invoicesService.findByUser(userId, pagination)

    @GetMapping("/{id}")
    @Operation(summary = "Get invoice details")
    @Parameter(name = "id", description = "Invoice ID")
    @ApiResponse(responseCode = "200", description = "Invoice details including order info")
    @ApiResponse(responseCode = "404", description = "Invoice not found")
    fun findOne(
        @PathVariable id: String,
        @CurrentUser userId: String,
    ): InvoiceDetailDto = invoicesService.findOne(id, userId)
}
