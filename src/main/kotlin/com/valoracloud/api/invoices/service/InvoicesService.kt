package com.valoracloud.api.invoices.service

import com.valoracloud.api.common.dto.PaginatedResponse
import com.valoracloud.api.common.dto.PaginationDto
import com.valoracloud.api.common.exceptions.NotFoundException
import com.valoracloud.api.common.model.ServiceType
import com.valoracloud.api.config.DomainRepository
import com.valoracloud.api.config.InvoiceRepository
import com.valoracloud.api.config.OrderRepository
import com.valoracloud.api.config.PlanRepository
import org.springframework.stereotype.Service
import java.math.RoundingMode

@Service
class InvoicesService(
    private val invoiceRepo: InvoiceRepository,
    private val orderRepo: OrderRepository,
    private val planRepo: PlanRepository,
    private val domainRepo: DomainRepository,
) {

    fun findByUser(userId: String, pagination: PaginationDto): PaginatedResponse<InvoiceDto> {
        val allInvoices = invoiceRepo.findByUserIdOrderByCreatedAtDesc(userId)
        val total = allInvoices.size.toLong()
        val from = pagination.offset.coerceAtMost(allInvoices.size)
        val to = (from + pagination.limit).coerceAtMost(allInvoices.size)
        val page = allInvoices.subList(from, to)

        val orderIds = page.map { it.orderId }.distinct()
        val orderMap = orderRepo.findAllById(orderIds).associateBy { it.id }

        val planIds = orderMap.values.mapNotNull { it.planId }.distinct()
        val planMap = planRepo.findAllById(planIds).associateBy { it.id }

        val domainOrderIds = orderMap.values
            .filter { it.serviceType == ServiceType.DOMAIN }
            .map { it.id }
        val domainMap = domainRepo.findByOrderIdIn(domainOrderIds).associateBy { it.orderId }

        return PaginatedResponse(
            data = page.map { invoice ->
                val order = orderMap[invoice.orderId]
                val plan = order?.planId?.let { planMap[it] }
                val domain = order?.let { domainMap[it.id] }
                invoice.toDto(order, plan, domain)
            },
            total = total,
            page = pagination.page,
            limit = pagination.limit,
            totalPages = ((total + pagination.limit - 1) / pagination.limit).toInt(),
        )
    }

    fun findOne(invoiceId: String, userId: String): InvoiceDetailDto {
        val invoice = invoiceRepo.findById(invoiceId).orElse(null)
            ?: throw NotFoundException("Invoice", invoiceId)
        if (invoice.userId != userId) throw NotFoundException("Invoice", invoiceId)

        val order = orderRepo.findById(invoice.orderId).orElse(null)
        val plan = order?.planId?.let { planRepo.findById(it).orElse(null) }
        val domain = order?.takeIf { it.serviceType == ServiceType.DOMAIN }
            ?.let { domainRepo.findByOrderId(it.id) }

        return invoice.toDetailDto(order, plan, domain)
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
    val planName: String?,
    val billingCycle: Int?,
    val region: String?,
    val hostname: String?,
    val domainName: String?,
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
    val planName: String?,
    val billingCycle: Int,
    val region: String,
    val os: String?,
    val hostname: String?,
    val domainName: String?,
    val totalAmount: String,
    val status: String,
    val createdAt: String,
)

private fun com.valoracloud.api.entity.Invoice.toDto(
    order: com.valoracloud.api.entity.Order?,
    plan: com.valoracloud.api.entity.Plan?,
    domain: com.valoracloud.api.entity.Domain?,
) = InvoiceDto(
    id = id,
    amount = amount.setScale(2, RoundingMode.HALF_UP).toPlainString(),
    currency = currency,
    paymentMethod = paymentMethod.name,
    paidAt = paidAt?.toString(),
    createdAt = createdAt.toString(),
    orderId = orderId,
    serviceType = order?.serviceType?.name,
    planId = order?.planId,
    planName = plan?.name,
    billingCycle = order?.billingCycle,
    region = order?.region,
    hostname = order?.hostname,
    domainName = domain?.domainName,
    orderStatus = order?.status?.name,
)

private fun com.valoracloud.api.entity.Invoice.toDetailDto(
    order: com.valoracloud.api.entity.Order?,
    plan: com.valoracloud.api.entity.Plan?,
    domain: com.valoracloud.api.entity.Domain?,
) = InvoiceDetailDto(
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
    order = order?.let {
        InvoiceOrderDto(
            id = it.id,
            serviceType = it.serviceType.name,
            planId = it.planId,
            planName = plan?.name,
            billingCycle = it.billingCycle,
            region = it.region,
            os = it.os,
            hostname = it.hostname,
            domainName = domain?.domainName,
            totalAmount = it.totalAmount.setScale(2, RoundingMode.HALF_UP).toPlainString(),
            status = it.status.name,
            createdAt = it.createdAt.toString(),
        )
    },
)
