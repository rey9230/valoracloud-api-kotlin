package com.valoracloud.api.invoices.service

import com.valoracloud.api.common.dto.PaginatedResponse
import com.valoracloud.api.common.dto.PaginationDto
import com.valoracloud.api.common.exceptions.NotFoundException
import com.valoracloud.api.config.InvoiceRepository
import org.springframework.stereotype.Service
import java.math.RoundingMode

@Service
class InvoicesService(
    private val invoiceRepo: InvoiceRepository,
) {

    fun findByUser(userId: String, pagination: PaginationDto): PaginatedResponse<InvoiceDto> {
        val allInvoices = invoiceRepo.findByUserIdOrderByCreatedAtDesc(userId)
        val total = allInvoices.size.toLong()
        val from = pagination.offset.coerceAtMost(allInvoices.size)
        val to = (from + pagination.limit).coerceAtMost(allInvoices.size)
        val page = allInvoices.subList(from, to)

        return PaginatedResponse(
            data = page.map { it.toDto() },
            total = total,
            page = pagination.page,
            limit = pagination.limit,
            totalPages = ((total + pagination.limit - 1) / pagination.limit).toInt(),
        )
    }

    fun findOne(invoiceId: String, userId: String): InvoiceDetailDto {
        val invoice = invoiceRepo.findById(invoiceId).orElse(null)
            ?: throw NotFoundException("Invoice", invoiceId)

        if (invoice.userId != userId) {
            throw NotFoundException("Invoice", invoiceId)
        }

        return invoice.toDetailDto()
    }
}

data class InvoiceDto(
    val id: String,
    val amount: String,
    val currency: String,
    val paymentMethod: String,
    val paidAt: String?,
    val createdAt: String,
    val orderId: String?,
    val serviceType: String?,
    val planId: String?,
    val billingCycle: Int?,
    val region: String?,
    val orderStatus: String?,
)

data class InvoiceDetailDto(
    val id: String,
    val amount: String,
    val currency: String,
    val paymentMethod: String,
    val stripeInvoiceId: String?,
    val cryptoTxId: String?,
    val cryptoCurrency: String?,
    val cryptoAmount: String?,
    val paidAt: String?,
    val createdAt: String,
    val order: InvoiceOrderDto?,
)

data class InvoiceOrderDto(
    val id: String,
    val serviceType: String,
    val planId: String?,
    val billingCycle: Int,
    val region: String,
    val os: String?,
    val totalAmount: String,
    val status: String,
    val createdAt: String,
)

private fun com.valoracloud.api.entity.Invoice.toDto() = InvoiceDto(
    id = id,
    amount = amount.setScale(2, RoundingMode.HALF_UP).toPlainString(),
    currency = currency,
    paymentMethod = paymentMethod.name,
    paidAt = paidAt?.toString(),
    createdAt = createdAt.toString(),
    orderId = orderId,
    serviceType = null,
    planId = null,
    billingCycle = null,
    region = null,
    orderStatus = null,
)

private fun com.valoracloud.api.entity.Invoice.toDetailDto() = InvoiceDetailDto(
    id = id,
    amount = amount.setScale(2, RoundingMode.HALF_UP).toPlainString(),
    currency = currency,
    paymentMethod = paymentMethod.name,
    stripeInvoiceId = stripeInvoiceId,
    cryptoTxId = cryptoTxId,
    cryptoCurrency = cryptoCurrency,
    cryptoAmount = cryptoAmount,
    paidAt = paidAt?.toString(),
    createdAt = createdAt.toString(),
    order = null,
)
