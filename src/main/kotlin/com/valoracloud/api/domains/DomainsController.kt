package com.valoracloud.api.domains

import com.valoracloud.api.auth.security.CurrentUser
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/domains")
class DomainsController(
    private val domainsService: DomainsService,
) {
    // ─── Public Endpoints ────────────────────────────────

    @PostMapping("/check")
    fun checkAvailability(@Valid @RequestBody dto: CheckDomainDto) =
        domainsService.checkAvailability(dto.domain)

    @GetMapping("/tlds")
    fun listTlds() = domainsService.listTldPricing()

    // ─── Domain Checkout ─────────────────────────────────

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    fun checkoutDomain(
        @CurrentUser userId: String,
        @Valid @RequestBody dto: CheckoutDomainDto,
    ) = domainsService.checkoutDomain(userId, dto)

    // ─── User Domains ────────────────────────────────────

    @GetMapping
    fun listDomains(@CurrentUser userId: String) =
        domainsService.listUserDomains(userId)

    @GetMapping("/{id}")
    fun getDomainDetail(
        @CurrentUser userId: String,
        @PathVariable id: String,
    ) = domainsService.getDomainDetail(userId, id)

    @PostMapping("/{id}/auth-code")
    fun getAuthCode(
        @CurrentUser userId: String,
        @PathVariable id: String,
    ) = domainsService.getAuthCode(userId, id)

    @PatchMapping("/{id}")
    fun updateDomain(
        @CurrentUser userId: String,
        @PathVariable id: String,
        @Valid @RequestBody dto: UpdateDomainDto,
    ) = domainsService.updateDomain(userId, id, dto)

    @PostMapping("/{id}/cancel")
    fun cancelDomain(
        @CurrentUser userId: String,
        @PathVariable id: String,
        @Valid @RequestBody dto: CancelDomainDto,
    ) = domainsService.cancelDomain(userId, id, dto)

    @PostMapping("/{id}/revoke-cancellation")
    fun revokeCancellation(
        @CurrentUser userId: String,
        @PathVariable id: String,
    ) = domainsService.revokeCancellation(userId, id)

    @PostMapping("/{id}/transfer-out")
    fun confirmTransferOut(
        @CurrentUser userId: String,
        @PathVariable id: String,
    ) = domainsService.confirmTransferOut(userId, id)

    @DeleteMapping("/{id}/transfer-out")
    fun revokeTransferOut(
        @CurrentUser userId: String,
        @PathVariable id: String,
    ) = domainsService.revokeTransferOut(userId, id)

    // ─── Domain Handles ──────────────────────────────────

    @PostMapping("/handles/sync")
    fun syncHandles(@CurrentUser userId: String) =
        domainsService.syncHandlesFromContabo(userId)

    @GetMapping("/handles")
    fun listHandles(@CurrentUser userId: String) =
        domainsService.listUserHandles(userId)

    @PostMapping("/handles")
    @ResponseStatus(HttpStatus.CREATED)
    fun createHandle(
        @CurrentUser userId: String,
        @Valid @RequestBody dto: CreateDomainHandleDto,
    ) = domainsService.createHandle(userId, dto)

    @PatchMapping("/handles/{handleId}")
    fun updateHandle(
        @CurrentUser userId: String,
        @PathVariable handleId: String,
        @Valid @RequestBody dto: UpdateDomainHandleDto,
    ) = domainsService.updateHandle(userId, handleId, dto)

    @DeleteMapping("/handles/{handleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteHandle(
        @CurrentUser userId: String,
        @PathVariable handleId: String,
    ) = domainsService.deleteHandle(userId, handleId)

    // ─── DNS Records ─────────────────────────────────────

    @GetMapping("/{id}/dns/records")
    fun listDnsRecords(
        @CurrentUser userId: String,
        @PathVariable id: String,
    ) = domainsService.listDnsRecords(userId, id)

    @PostMapping("/{id}/dns/records")
    @ResponseStatus(HttpStatus.CREATED)
    fun createDnsRecord(
        @CurrentUser userId: String,
        @PathVariable id: String,
        @Valid @RequestBody dto: CreateDnsRecordDto,
    ) = domainsService.createDnsRecord(userId, id, dto)

    @PatchMapping("/{id}/dns/records/{recordId}")
    fun updateDnsRecord(
        @CurrentUser userId: String,
        @PathVariable id: String,
        @PathVariable recordId: String,
        @Valid @RequestBody dto: CreateDnsRecordDto,
    ) = domainsService.updateDnsRecord(userId, id, recordId, dto)

    @DeleteMapping("/{id}/dns/records/{recordId}")
    fun deleteDnsRecord(
        @CurrentUser userId: String,
        @PathVariable id: String,
        @PathVariable recordId: String,
    ) = domainsService.deleteDnsRecord(userId, id, recordId)

    // ─── DNS Zones ───────────────────────────────────────

    @GetMapping("/{id}/dns/zones")
    fun listDnsZones(
        @CurrentUser userId: String,
        @PathVariable id: String,
    ) = domainsService.listDnsZones(userId, id)

    @PostMapping("/{id}/dns/zones")
    @ResponseStatus(HttpStatus.CREATED)
    fun createDnsZone(
        @CurrentUser userId: String,
        @PathVariable id: String,
    ) = domainsService.createDnsZone(userId, id)

    @DeleteMapping("/{id}/dns/zones/{zoneName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteDnsZone(
        @CurrentUser userId: String,
        @PathVariable id: String,
        @PathVariable zoneName: String,
    ) = domainsService.deleteDnsZone(userId, id, zoneName)
}
