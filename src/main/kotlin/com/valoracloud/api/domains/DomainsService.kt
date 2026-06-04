package com.valoracloud.api.domains

import com.valoracloud.api.common.exceptions.BadRequestException
import com.valoracloud.api.common.exceptions.NotFoundException
import com.valoracloud.api.config.*
import com.valoracloud.api.contabo.ContaboService
import com.valoracloud.api.notifications.service.NotificationsService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class DomainsService(
    private val domainRepository: DomainRepository,
    private val domainHandleRepository: DomainHandleRepository,
    private val domainTldPricingRepository: DomainTldPricingRepository,
    private val contabo: ContaboService,
    private val notifications: NotificationsService,
    // TODO: Inject EncryptionUtil, Stripe, SHKeeper, ProvisioningQueue, ConfigService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // ─── Public ────────────────────────────────────────────

    fun checkAvailability(domain: String): Map<String, Any?> {
        val parts = domain.split(".")
        if (parts.size < 2) throw BadRequestException("Invalid domain name")
        val tld = ".${parts.drop(1).joinToString(".")}"

        val tldPricing = domainTldPricingRepository.findByTld(tld)
        if (tldPricing == null || !tldPricing.available) {
            return mapOf("domain" to domain, "available" to false, "supported" to false, "price" to null)
        }

        // TODO: ContaboService.checkDomainAvailability(domain)
        return mapOf(
            "domain" to domain,
            "available" to true,
            "supported" to true,
            "price" to tldPricing.registrationPrice.toDouble(),
            "renewalPrice" to tldPricing.renewalPrice.toDouble(),
        )
    }

    fun listTldPricing() = domainTldPricingRepository.findByAvailableTrue().map {
        mapOf(
            "id" to it.id, "tld" to it.tld,
            "registrationPrice" to it.registrationPrice.toDouble(),
            "renewalPrice" to it.renewalPrice.toDouble(),
            "transferPrice" to it.transferPrice.toDouble(),
            "available" to it.available,
        )
    }

    // ─── User Domains ──────────────────────────────────────

    fun listUserDomains(userId: String) =
        domainRepository.findByUserIdOrderByCreatedAtDesc(userId)

    fun getDomainDetail(userId: String, domainId: String): Any {
        val domain = domainRepository.findById(domainId)
            .orElseThrow { NotFoundException("Domain", domainId) }
        if (domain.userId != userId) throw NotFoundException("Domain", domainId)
        return domain
    }

    fun getAuthCode(userId: String, domainId: String): Map<String, String> {
        val domain = domainRepository.findById(domainId)
            .orElseThrow { NotFoundException("Domain", domainId) }
        if (domain.userId != userId) throw NotFoundException("Domain", domainId)

        // TODO: ContaboService.generateAuthCode(domain.domainName)
        return mapOf("authCode" to "")
    }

    // ─── Handles ───────────────────────────────────────────

    fun listUserHandles(userId: String) =
        domainHandleRepository.findByUserId(userId)

    fun createHandle(userId: String, dto: CreateDomainHandleDto): Any {
        // TODO: Create handle in Contabo, then store in DB
        return mapOf("message" to "Handle creation not yet implemented — requires ContaboService")
    }

    fun syncHandlesFromContabo(userId: String): Map<String, Int> {
        // TODO: ContaboService.listDomainHandles() → upsert into DB
        return mapOf("synced" to 0, "skipped" to 0, "total" to 0)
    }

    fun updateHandle(userId: String, handleId: String, dto: UpdateDomainHandleDto): Any {
        val handle = domainHandleRepository.findById(handleId)
            .orElseThrow { NotFoundException("Handle", handleId) }
        if (handle.userId != userId) throw NotFoundException("Handle", handleId)
        return handle
    }

    fun deleteHandle(userId: String, handleId: String) {
        val handle = domainHandleRepository.findById(handleId)
            .orElseThrow { NotFoundException("Handle", handleId) }
        if (handle.userId != userId) throw NotFoundException("Handle", handleId)
        domainHandleRepository.delete(handle)
    }

    // ─── Checkout ──────────────────────────────────────────

    fun checkoutDomain(userId: String, dto: CheckoutDomainDto): Any {
        // TODO: Full domain checkout — validate TLD, resolve handles,
        // check availability, create order + domain, handle payment
        throw UnsupportedOperationException("Domain checkout not yet implemented — needs ContaboService, Stripe, SHKeeper")
    }

    // ─── Domain Management ─────────────────────────────────

    fun updateDomain(userId: String, domainId: String, dto: UpdateDomainDto): Any {
        val domain = domainRepository.findById(domainId)
            .orElseThrow { NotFoundException("Domain", domainId) }
        if (domain.userId != userId) throw NotFoundException("Domain", domainId)
        // TODO: ContaboService.updateDomain(domain.domainName, dto)
        return domain
    }

    fun cancelDomain(userId: String, domainId: String, dto: CancelDomainDto): Any {
        val domain = domainRepository.findById(domainId)
            .orElseThrow { NotFoundException("Domain", domainId) }
        if (domain.userId != userId) throw NotFoundException("Domain", domainId)

        val cancelDate = dto.cancelDate ?: endOfMonthDate()
        try {
            contabo.cancelDomain(domain.domainName, dto.reason, cancelDate)
            log.info("Domain ${domain.domainName} cancellation scheduled in Contabo for $cancelDate")
        } catch (e: Exception) {
            notifications.sendCancellationFailureAlert(
                serviceType = "DOMAIN",
                resourceId = domain.id,
                contaboId = domain.domainName,
                userId = domain.userId,
                errorMessage = e.message ?: "Unknown error",
                errorStack = e.stackTraceToString(),
            )
            throw e
        }

        domain.status = com.valoracloud.api.common.model.DomainStatus.CANCELLED
        return domainRepository.save(domain)
    }

    fun revokeCancellation(userId: String, domainId: String): Any {
        val domain = domainRepository.findById(domainId)
            .orElseThrow { NotFoundException("Domain", domainId) }
        if (domain.userId != userId) throw NotFoundException("Domain", domainId)

        contabo.revokeDomainCancellation(domain.domainName)
        log.info("Domain ${domain.domainName} cancellation revoked in Contabo")

        domain.status = com.valoracloud.api.common.model.DomainStatus.ACTIVE
        return domainRepository.save(domain)
    }

    fun confirmTransferOut(userId: String, domainId: String): Any {
        val domain = domainRepository.findById(domainId)
            .orElseThrow { NotFoundException("Domain", domainId) }
        if (domain.userId != userId) throw NotFoundException("Domain", domainId)

        contabo.confirmTransferOut(domain.domainName)
        log.info("Domain ${domain.domainName} transfer-out confirmed in Contabo")

        domain.status = com.valoracloud.api.common.model.DomainStatus.TRANSFER_IN_PROGRESS
        return domainRepository.save(domain)
    }

    fun revokeTransferOut(userId: String, domainId: String): Any {
        val domain = domainRepository.findById(domainId)
            .orElseThrow { NotFoundException("Domain", domainId) }
        if (domain.userId != userId) throw NotFoundException("Domain", domainId)

        contabo.revokeTransferOut(domain.domainName)
        log.info("Domain ${domain.domainName} transfer-out revoked in Contabo")

        domain.status = com.valoracloud.api.common.model.DomainStatus.ACTIVE
        return domainRepository.save(domain)
    }

    // ─── DNS Records ───────────────────────────────────────

    fun listDnsRecords(userId: String, domainId: String): Any {
        val domain = domainRepository.findById(domainId)
            .orElseThrow { NotFoundException("Domain", domainId) }
        if (domain.userId != userId) throw NotFoundException("Domain", domainId)
        // TODO: ContaboService.listDnsRecords(domain.domainName)
        return emptyList<Any>()
    }

    fun createDnsRecord(userId: String, domainId: String, dto: CreateDnsRecordDto): Any {
        val domain = domainRepository.findById(domainId)
            .orElseThrow { NotFoundException("Domain", domainId) }
        if (domain.userId != userId) throw NotFoundException("Domain", domainId)
        // TODO: ContaboService.createDnsRecord(domain.domainName, dto)
        return mapOf("success" to true)
    }

    fun updateDnsRecord(userId: String, domainId: String, recordId: String, dto: CreateDnsRecordDto): Any {
        val domain = domainRepository.findById(domainId)
            .orElseThrow { NotFoundException("Domain", domainId) }
        if (domain.userId != userId) throw NotFoundException("Domain", domainId)
        // TODO: ContaboService.updateDnsRecord(domain.domainName, recordId, dto)
        return mapOf("success" to true)
    }

    fun deleteDnsRecord(userId: String, domainId: String, recordId: String): Any {
        val domain = domainRepository.findById(domainId)
            .orElseThrow { NotFoundException("Domain", domainId) }
        if (domain.userId != userId) throw NotFoundException("Domain", domainId)
        // TODO: ContaboService.deleteDnsRecord(domain.domainName, recordId)
        return mapOf("success" to true)
    }

    // ─── DNS Zones ─────────────────────────────────────────

    fun listDnsZones(userId: String, domainId: String): Any {
        val domain = domainRepository.findById(domainId)
            .orElseThrow { NotFoundException("Domain", domainId) }
        if (domain.userId != userId) throw NotFoundException("Domain", domainId)
        // TODO: ContaboService.listDnsZones()
        return emptyList<Any>()
    }

    fun createDnsZone(userId: String, domainId: String): Any {
        val domain = domainRepository.findById(domainId)
            .orElseThrow { NotFoundException("Domain", domainId) }
        if (domain.userId != userId) throw NotFoundException("Domain", domainId)
        // TODO: ContaboService.createDnsZone(domain.domainName)
        return mapOf("success" to true)
    }

    fun deleteDnsZone(userId: String, domainId: String, zoneName: String): Any {
        val domain = domainRepository.findById(domainId)
            .orElseThrow { NotFoundException("Domain", domainId) }
        if (domain.userId != userId) throw NotFoundException("Domain", domainId)
        // TODO: ContaboService.deleteDnsZone(zoneName)
        return mapOf("success" to true)
    }

    private fun endOfMonthDate(): String {
        val now = LocalDate.now()
        return now.withDayOfMonth(now.lengthOfMonth()).toString()
    }
}
