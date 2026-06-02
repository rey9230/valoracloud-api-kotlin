package com.valoracloud.api.admin.controller

import com.valoracloud.api.admin.dto.*
import com.valoracloud.api.admin.service.AdminService
import com.valoracloud.api.common.dto.PaginationDto
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
class AdminController(private val admin: AdminService) {

    // ════════════════════════════════════════════════════════════
    // Stats
    // ════════════════════════════════════════════════════════════

    @GetMapping("/stats")
    fun getStats(@ModelAttribute period: StatsPeriodDto) = admin.getStats(period)

    @GetMapping("/stats/revenue")
    fun getRevenueStats(@ModelAttribute dto: StatsPeriodDto) = admin.getRevenueStats(dto)

    @GetMapping("/stats/new-users")
    fun getNewUsersStats(@ModelAttribute dto: StatsPeriodDto) = admin.getNewUsersStats(dto)

    @GetMapping("/stats/servers-by-region")
    fun getServersByRegion() = admin.getServersByRegion()

    @GetMapping("/stats/orders-by-status")
    fun getOrdersByStatus() = admin.getOrdersByStatus()

    // ════════════════════════════════════════════════════════════
    // Users
    // ════════════════════════════════════════════════════════════

    @GetMapping("/users")
    fun listUsers(@ModelAttribute filter: AdminUsersFilterDto) = admin.listUsers(filter)

    @GetMapping("/users/{userId}")
    fun getUserDetail(@PathVariable userId: String) = admin.getUserDetail(userId)

    @GetMapping("/users/{userId}/sessions")
    fun getUserSessions(@PathVariable userId: String) = admin.getUserSessions(userId)

    @DeleteMapping("/users/{userId}/sessions")
    fun revokeUserSessions(@PathVariable userId: String) = admin.revokeUserSessions(userId)

    @PatchMapping("/users/{userId}/role")
    fun updateUserRole(@PathVariable userId: String, @RequestBody dto: UpdateUserRoleDto) =
        admin.updateUserRole(userId, dto)

    @PatchMapping("/users/{userId}/status")
    fun updateUserStatus(@PathVariable userId: String, @RequestBody dto: UpdateUserStatusDto) =
        admin.updateUserStatus(userId, dto)

    @DeleteMapping("/users/{userId}")
    fun deleteUser(@PathVariable userId: String) = admin.deleteUser(userId)

    @PostMapping("/users/{userId}/restore")
    fun restoreUser(@PathVariable userId: String) = admin.restoreUser(userId)

    @PostMapping("/users/{userId}/verify-email")
    fun forceVerifyEmail(@PathVariable userId: String) = admin.forceVerifyEmail(userId)

    @PostMapping("/users/{userId}/impersonate")
    fun impersonateUser(@PathVariable userId: String) = admin.impersonateUser(userId)

    // ════════════════════════════════════════════════════════════
    // Servers
    // ════════════════════════════════════════════════════════════

    @GetMapping("/servers")
    fun listServers(@ModelAttribute filter: AdminServersFilterDto) = admin.listServers(filter)

    @GetMapping("/servers/{serverId}")
    fun getServerDetail(@PathVariable serverId: String) = admin.getServerDetail(serverId)

    @GetMapping("/servers/{serverId}/credentials")
    fun getServerCredentials(@PathVariable serverId: String) = admin.getServerCredentials(serverId)

    @GetMapping("/servers/{serverId}/console")
    fun getServerConsole(@PathVariable serverId: String) = admin.getServerConsole(serverId)

    @GetMapping("/servers/{serverId}/logs")
    fun getServerLogs(@PathVariable serverId: String) = admin.adminGetServerLogs(serverId)

    @PostMapping("/servers/{serverId}/start")
    fun startServer(@PathVariable serverId: String) = admin.adminStartServer(serverId)

    @PostMapping("/servers/{serverId}/stop")
    fun stopServer(@PathVariable serverId: String) = admin.adminStopServer(serverId)

    @PostMapping("/servers/{serverId}/restart")
    fun restartServer(@PathVariable serverId: String) = admin.adminRestartServer(serverId)

    @PostMapping("/servers/{serverId}/suspend")
    fun suspendServer(@PathVariable serverId: String) = admin.adminSuspendServer(serverId)

    @PostMapping("/servers/{serverId}/unsuspend")
    fun unsuspendServer(@PathVariable serverId: String) = admin.adminUnsuspendServer(serverId)

    @PostMapping("/servers/{serverId}/reprovision")
    fun reprovisionServer(@PathVariable serverId: String) = admin.adminReprovisionServer(serverId)

    @PostMapping("/servers/{serverId}/sync")
    fun syncServer(@PathVariable serverId: String) = admin.adminSyncServer(serverId)

    @PatchMapping("/servers/{serverId}/status")
    fun updateServerStatus(@PathVariable serverId: String, @RequestBody dto: UpdateServerStatusDto) =
        admin.updateServerStatus(serverId, dto)

    @PostMapping("/servers/{serverId}/reinstall")
    fun adminReinstallServer(@PathVariable serverId: String, @RequestBody dto: ReinstallServerDto) =
        admin.adminReinstallServer(serverId, dto)

    @PatchMapping("/servers/{serverId}/password")
    fun adminChangeServerPassword(@PathVariable serverId: String, @RequestBody dto: ChangeServerPasswordDto) =
        admin.adminChangeServerPassword(serverId, dto)

    // ════════════════════════════════════════════════════════════
    // Orders
    // ════════════════════════════════════════════════════════════

    @GetMapping("/orders")
    fun listOrders(@ModelAttribute filter: AdminOrdersFilterDto) = admin.listOrders(filter)

    @GetMapping("/orders/{orderId}")
    fun getOrderDetail(@PathVariable orderId: String) = admin.getOrderDetail(orderId)

    @PatchMapping("/orders/{orderId}/status")
    fun updateOrderStatus(@PathVariable orderId: String, @RequestBody dto: UpdateOrderStatusDto) =
        admin.updateOrderStatus(orderId, dto)

    // ════════════════════════════════════════════════════════════
    // Invoices
    // ════════════════════════════════════════════════════════════

    @GetMapping("/invoices")
    fun listInvoices(@ModelAttribute filter: AdminInvoicesFilterDto) = admin.listInvoices(filter)

    @GetMapping("/invoices/{invoiceId}")
    fun getInvoiceDetail(@PathVariable invoiceId: String) = admin.getInvoiceDetail(invoiceId)

    // ════════════════════════════════════════════════════════════
    // Webhook Events
    // ════════════════════════════════════════════════════════════

    @GetMapping("/webhook-events")
    fun listWebhookEvents() = admin.listWebhookEvents()

    // ════════════════════════════════════════════════════════════
    // Sessions
    // ════════════════════════════════════════════════════════════

    @GetMapping("/sessions")
    fun getAllSessions(@ModelAttribute dto: PaginationDto) = admin.getAllSessions(dto)

    // ════════════════════════════════════════════════════════════
    // Plans
    // ════════════════════════════════════════════════════════════

    @GetMapping("/plans")
    fun listPlans(@ModelAttribute filter: AdminPlansFilterDto) = admin.listPlans(filter)

    @GetMapping("/plans/{planId}")
    fun getPlanDetail(@PathVariable planId: String) = admin.getPlanDetail(planId)

    @PostMapping("/plans")
    fun createPlan(@RequestBody dto: CreatePlanDto) = admin.createPlan(dto)

    @PutMapping("/plans/{planId}")
    fun updatePlan(@PathVariable planId: String, @RequestBody dto: UpdatePlanDto) =
        admin.updatePlan(planId, dto)

    @DeleteMapping("/plans/{planId}")
    fun deletePlan(@PathVariable planId: String) = admin.deletePlan(planId)

    @PatchMapping("/plans/{planId}/status")
    fun updatePlanStatus(@PathVariable planId: String, @RequestBody dto: UpdatePlanStatusDto) =
        admin.updatePlanStatus(planId, dto)

    // ════════════════════════════════════════════════════════════
    // TLD Pricing
    // ════════════════════════════════════════════════════════════

    @GetMapping("/tld-pricing")
    fun listTldPricing() = admin.listTldPricing()

    @PostMapping("/tld-pricing")
    fun createTldPricing(@RequestBody dto: CreateTldPricingDto) = admin.createTldPricing(dto)

    @PatchMapping("/tld-pricing/{id}")
    fun updateTldPricing(@PathVariable("id") id: String, @RequestBody dto: UpdateTldPricingDto) =
        admin.updateTldPricing(id, dto)

    @DeleteMapping("/tld-pricing/{id}")
    fun deleteTldPricing(@PathVariable("id") id: String) = admin.deleteTldPricing(id)

    @PostMapping("/tld-pricing/seed")
    fun seedTldPricing() = admin.seedTldPricing()

    // ════════════════════════════════════════════════════════════
    // Domains
    // ════════════════════════════════════════════════════════════

    @GetMapping("/domains")
    fun getDomains(@ModelAttribute dto: AdminDomainsFilterDto) = admin.getDomains(dto)

    @GetMapping("/domains/{id}")
    fun getDomainDetail(@PathVariable("id") id: String) = admin.getDomainDetail(id)

    @PatchMapping("/domains/{id}/status")
    fun updateDomainStatus(@PathVariable("id") id: String, @RequestBody dto: UpdateDomainStatusDto) =
        admin.updateDomainStatus(id, dto)

    @PostMapping("/domains/{id}/sync")
    fun syncDomain(@PathVariable("id") id: String) = admin.adminSyncDomain(id)

    @PutMapping("/domains/{id}/nameservers")
    fun updateDomainNameservers(@PathVariable("id") id: String, @RequestBody dto: UpdateNameserversDto) =
        admin.adminUpdateDomainNameservers(id, dto)

    @PostMapping("/domains/{id}/cancel")
    fun adminCancelDomain(@PathVariable("id") id: String, @RequestBody dto: CancelDomainDto) =
        admin.adminCancelDomain(id, dto)

    @PostMapping("/domains/{id}/revoke-cancellation")
    fun adminRevokeDomainCancellation(@PathVariable("id") id: String) = admin.adminRevokeDomainCancellation(id)

    @PostMapping("/domains/{id}/auth-code")
    fun adminGetDomainAuthCode(@PathVariable("id") id: String) = admin.adminGetDomainAuthCode(id)

    @GetMapping("/users/{userId}/domain-handles")
    fun adminListDomainHandles(@PathVariable userId: String) = admin.adminListDomainHandles(userId)

    @PostMapping("/users/{userId}/domain-handles")
    fun adminCreateDomainHandle(@PathVariable userId: String, @RequestBody dto: Map<String, Any>) =
        admin.adminCreateDomainHandle(userId, dto)

    @PutMapping("/domain-handles/{id}")
    fun adminUpdateDomainHandle(@PathVariable("id") id: String, @RequestBody dto: Map<String, Any>) =
        admin.adminUpdateDomainHandle(id, dto)

    @DeleteMapping("/domain-handles/{id}")
    fun adminDeleteDomainHandle(@PathVariable("id") id: String) = admin.adminDeleteDomainHandle(id)

    // ════════════════════════════════════════════════════════════
    // Object Storage
    // ════════════════════════════════════════════════════════════

    @GetMapping("/object-storage")
    fun getObjectStorages(@ModelAttribute dto: AdminObjectStoragesFilterDto) = admin.getObjectStorages(dto)

    @GetMapping("/object-storage/{id}")
    fun getObjectStorageDetail(@PathVariable("id") id: String) = admin.getObjectStorageDetail(id)

    @PatchMapping("/object-storage/{id}/status")
    fun updateObjectStorageStatus(@PathVariable("id") id: String, @RequestBody dto: UpdateObjectStorageStatusDto) =
        admin.updateObjectStorageStatus(id, dto)

    @GetMapping("/object-storage/{id}/credentials")
    fun adminGetStorageCredentials(@PathVariable("id") id: String) = admin.adminGetStorageCredentials(id)

    @PutMapping("/object-storage/{id}/upgrade")
    fun adminUpgradeStorage(@PathVariable("id") id: String, @RequestBody dto: UpgradeStorageDto) =
        admin.adminUpgradeStorage(id, dto)

    @DeleteMapping("/object-storage/{id}")
    fun adminCancelStorage(@PathVariable("id") id: String, @RequestBody dto: CancelStorageDto) =
        admin.adminCancelStorage(id, dto)

    @PostMapping("/object-storage/{id}/sync")
    fun syncObjectStorage(@PathVariable("id") id: String) = admin.adminSyncObjectStorage(id)

    // ════════════════════════════════════════════════════════════
    // Firewalls
    // ════════════════════════════════════════════════════════════

    @GetMapping("/firewalls")
    fun getFirewalls(@ModelAttribute dto: AdminFirewallsFilterDto) = admin.getFirewalls(dto)

    @GetMapping("/firewalls/{id}")
    fun getFirewallDetail(@PathVariable("id") id: String) = admin.getFirewallDetail(id)

    @PostMapping("/users/{userId}/firewalls")
    fun adminCreateFirewall(@PathVariable userId: String, @RequestBody dto: Map<String, Any>) =
        admin.adminCreateFirewall(userId, dto)

    @PutMapping("/firewalls/{id}")
    fun adminUpdateFirewall(@PathVariable("id") id: String, @RequestBody dto: Map<String, Any>) =
        admin.adminUpdateFirewall(id, dto)

    @PutMapping("/firewalls/{id}/rules")
    fun adminUpdateFirewallRules(@PathVariable("id") id: String, @RequestBody dto: Map<String, Any>) =
        admin.adminUpdateFirewallRules(id, dto)

    @DeleteMapping("/firewalls/{id}")
    fun adminDeleteFirewall(@PathVariable("id") id: String) = admin.adminDeleteFirewall(id)

    @PostMapping("/firewalls/{firewallId}/servers/{serverId}")
    fun adminAssignFirewallToServer(@PathVariable firewallId: String, @PathVariable serverId: String) =
        admin.adminAssignFirewallToServer(firewallId, serverId)

    @DeleteMapping("/firewalls/{firewallId}/servers/{serverId}")
    fun adminUnassignFirewallFromServer(@PathVariable firewallId: String, @PathVariable serverId: String) =
        admin.adminUnassignFirewallFromServer(firewallId, serverId)

    // ════════════════════════════════════════════════════════════
    // Snapshots
    // ════════════════════════════════════════════════════════════

    @GetMapping("/snapshots")
    fun getSnapshots(@ModelAttribute dto: AdminSnapshotsFilterDto) = admin.getSnapshots(dto)

    @GetMapping("/snapshots/{id}")
    fun getSnapshotDetail(@PathVariable("id") id: String) = admin.getSnapshotDetail(id)

    @PostMapping("/servers/{serverId}/snapshots")
    fun adminCreateSnapshot(@PathVariable serverId: String, @RequestBody dto: CreateSnapshotDto) =
        admin.adminCreateSnapshot(serverId, dto)

    @PutMapping("/snapshots/{id}")
    fun adminUpdateSnapshot(@PathVariable("id") id: String, @RequestBody dto: UpdateSnapshotDto) =
        admin.adminUpdateSnapshot(id, dto)

    @DeleteMapping("/snapshots/{id}")
    fun adminDeleteSnapshot(@PathVariable("id") id: String) = admin.adminDeleteSnapshot(id)

    @PostMapping("/snapshots/{id}/rollback")
    fun rollbackSnapshot(@PathVariable("id") id: String) = admin.adminRollbackSnapshot(id)

    @PostMapping("/servers/{serverId}/sync-snapshots")
    fun syncServerSnapshots(@PathVariable serverId: String) = admin.adminSyncSnapshots(serverId)

    // ════════════════════════════════════════════════════════════
    // Private Networks
    // ════════════════════════════════════════════════════════════

    @GetMapping("/private-networks")
    fun getPrivateNetworks(@ModelAttribute dto: AdminPrivateNetworksFilterDto) = admin.getPrivateNetworks(dto)

    @GetMapping("/private-networks/{id}")
    fun getPrivateNetworkDetail(@PathVariable("id") id: String) = admin.getPrivateNetworkDetail(id)

    @PostMapping("/users/{userId}/private-networks")
    fun adminCreatePrivateNetwork(@PathVariable userId: String, @RequestBody dto: Map<String, Any>) =
        admin.adminCreatePrivateNetwork(userId, dto)

    @PutMapping("/private-networks/{id}")
    fun adminUpdatePrivateNetwork(@PathVariable("id") id: String, @RequestBody dto: Map<String, Any>) =
        admin.adminUpdatePrivateNetwork(id, dto)

    @DeleteMapping("/private-networks/{id}")
    fun adminDeletePrivateNetwork(@PathVariable("id") id: String) = admin.adminDeletePrivateNetwork(id)

    @PostMapping("/private-networks/{networkId}/servers/{serverId}")
    fun adminAssignServerToNetwork(@PathVariable networkId: String, @PathVariable serverId: String) =
        admin.adminAssignServerToNetwork(networkId, serverId)

    @DeleteMapping("/private-networks/{networkId}/servers/{serverId}")
    fun adminUnassignServerFromNetwork(@PathVariable networkId: String, @PathVariable serverId: String) =
        admin.adminUnassignServerFromNetwork(networkId, serverId)

    // ════════════════════════════════════════════════════════════
    // VIPs
    // ════════════════════════════════════════════════════════════

    @GetMapping("/vips")
    fun getVips(@ModelAttribute dto: AdminVipsFilterDto) = admin.getVips(dto)

    @GetMapping("/vips/{id}")
    fun getVipDetail(@PathVariable("id") id: String) = admin.getVipDetail(id)

    @PostMapping("/users/{userId}/vips/sync")
    fun adminSyncVips(@PathVariable userId: String) = admin.adminSyncVips(userId)

    @PostMapping("/vips/{ip}/servers/{serverId}")
    fun adminAssignVipToServer(@PathVariable("ip") ip: String, @PathVariable serverId: String) =
        admin.adminAssignVipToServer(ip, serverId)

    @DeleteMapping("/vips/{ip}/servers/{serverId}")
    fun adminUnassignVipFromServer(@PathVariable("ip") ip: String, @PathVariable serverId: String) =
        admin.adminUnassignVipFromServer(ip, serverId)

    // ════════════════════════════════════════════════════════════
    // Secrets
    // ════════════════════════════════════════════════════════════

    @GetMapping("/users/{userId}/secrets")
    fun adminListSecrets(@PathVariable userId: String) = admin.adminListSecrets(userId)

    @PostMapping("/users/{userId}/secrets")
    fun adminCreateSecret(@PathVariable userId: String, @RequestBody dto: Map<String, Any>) =
        admin.adminCreateSecret(userId, dto)

    @DeleteMapping("/secrets/{id}")
    fun adminDeleteSecret(@PathVariable("id") id: String) = admin.adminDeleteSecret(id)

    @PostMapping("/users/{userId}/secrets/sync")
    fun adminSyncSecrets(@PathVariable userId: String) = admin.adminSyncSecrets(userId)

    // ════════════════════════════════════════════════════════════
    // Tags
    // ════════════════════════════════════════════════════════════

    @GetMapping("/users/{userId}/tags")
    fun adminListTags(@PathVariable userId: String) = admin.adminListTags(userId)

    @PostMapping("/users/{userId}/tags")
    fun adminCreateTag(@PathVariable userId: String, @RequestBody dto: Map<String, Any>) =
        admin.adminCreateTag(userId, dto)

    @DeleteMapping("/tags/{id}")
    fun adminDeleteTag(@PathVariable("id") id: String) = admin.adminDeleteTag(id)

    @PostMapping("/users/{userId}/tags/sync")
    fun adminSyncTags(@PathVariable userId: String) = admin.adminSyncTags(userId)

    @PostMapping("/tags/{id}/assign/{resourceType}/{resourceId}")
    fun adminAssignTag(
        @PathVariable("id") tagId: String,
        @PathVariable resourceType: String,
        @PathVariable resourceId: String,
    ) = admin.adminAssignTag(tagId, resourceType, resourceId)

    @DeleteMapping("/tags/{id}/assign/{resourceType}/{resourceId}")
    fun adminUnassignTag(
        @PathVariable("id") tagId: String,
        @PathVariable resourceType: String,
        @PathVariable resourceId: String,
    ) = admin.adminUnassignTag(tagId, resourceType, resourceId)

    // ════════════════════════════════════════════════════════════
    // Broadcast Notifications
    // ════════════════════════════════════════════════════════════

    @PostMapping("/notifications/maintenance")
    fun sendMaintenance(@RequestBody dto: SendMaintenanceDto) = admin.sendMaintenance(dto)

    @PostMapping("/notifications/incident")
    fun sendIncident(@RequestBody dto: SendIncidentDto) = admin.sendIncident(dto)
}
