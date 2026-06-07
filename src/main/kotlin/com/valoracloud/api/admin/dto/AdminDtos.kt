package com.valoracloud.api.admin.dto

import com.valoracloud.api.common.dto.PaginatedResponse
import java.math.BigDecimal
import java.time.Instant

// ─── Stats ──────────────────────────────────────────────────────
data class AdminStatsDto(
    val users: UsersStats,
    val servers: ServersStats,
    val orders: OrdersStats,
    val revenue: RevenueStats,
    val sessions: SessionsStats,
    val webhooks: WebhooksStats,
)

data class UsersStats(val total: Long, val deleted: Long, val unverified: Long)
data class ServersStats(val total: Long, val byStatus: Map<String, Long>)
data class OrdersStats(val total: Long, val byStatus: Map<String, Long>)
data class RevenueStats(val fromOrders: BigDecimal, val fromInvoices: BigDecimal, val totalInvoices: Long)
data class SessionsStats(val active: Long)
data class WebhooksStats(val unprocessed: Long)

data class RevenueSeriesEntry(val date: String, val revenue: Double)
data class RevenueStatsDto(val period: String, val series: List<RevenueSeriesEntry>)

data class NewUsersSeriesEntry(val date: String, val count: Int)
data class NewUsersStatsDto(val period: String, val series: List<NewUsersSeriesEntry>)

data class RegionCount(val region: String, val count: Long)
data class StatusCount(val status: String, val count: Long)

// ─── Filter DTOs ────────────────────────────────────────────────
data class AdminUsersFilterDto(
    val page: Int = 1,
    val limit: Int = 20,
    val search: String? = null,
    val role: String? = null,
    val status: String? = null,
    val emailVerified: Boolean? = null,
    val includeDeleted: Boolean = false,
)

data class AdminServersFilterDto(
    val page: Int = 1,
    val limit: Int = 20,
    val status: String? = null,
    val userId: String? = null,
    val region: String? = null,
)

data class AdminOrdersFilterDto(
    val page: Int = 1,
    val limit: Int = 20,
    val status: String? = null,
    val userId: String? = null,
)

data class AdminInvoicesFilterDto(
    val page: Int = 1,
    val limit: Int = 20,
    val userId: String? = null,
    val orderId: String? = null,
    val paymentMethod: String? = null,
    val dateFrom: String? = null,
    val dateTo: String? = null,
)

data class AdminWebhookEventsFilterDto(
    val page: Int = 1,
    val limit: Int = 20,
    val processed: Boolean? = null,
)

data class AdminPlansFilterDto(
    val page: Int = 1,
    val limit: Int = 20,
    val productType: String? = null,
    val search: String? = null,
    val status: String? = null,
)

data class AdminDomainsFilterDto(
    val page: Int = 1,
    val limit: Int = 20,
    val userId: String? = null,
    val status: String? = null,
)

data class AdminObjectStoragesFilterDto(
    val page: Int = 1,
    val limit: Int = 20,
    val userId: String? = null,
    val status: String? = null,
)

data class AdminFirewallsFilterDto(
    val page: Int = 1,
    val limit: Int = 20,
    val userId: String? = null,
)

data class AdminSnapshotsFilterDto(
    val page: Int = 1,
    val limit: Int = 20,
    val serverId: String? = null,
)

data class AdminPrivateNetworksFilterDto(
    val page: Int = 1,
    val limit: Int = 20,
    val userId: String? = null,
)

data class AdminVipsFilterDto(
    val page: Int = 1,
    val limit: Int = 20,
    val userId: String? = null,
)

// ─── Update DTOs ────────────────────────────────────────────────
data class UpdateUserRoleDto(val role: String)
data class UpdateUserStatusDto(val status: String)
data class UpdateOrderStatusDto(val status: String)
data class UpdateServerStatusDto(val status: String)
data class UpdatePlanStatusDto(val status: String)
data class UpdateDomainStatusDto(val status: String)
data class UpdateObjectStorageStatusDto(val status: String)

// ─── Plan DTOs ──────────────────────────────────────────────────
data class StatsPeriodDto(
    val period: String = "30d", // 7d | 30d | 90d
)

data class CreatePlanDto(
    val name: String,
    val slug: String,
    val productType: String,
    val description: String? = null,
    val cpu: Int, val ram: Int, val disk: Int,
    val diskType: String = "NVMe",
    val bandwidth: Int, val portSpeed: Int? = null,
    val snapshots: Int = 0,
    val priceMonthly: BigDecimal,
    val price1Month: BigDecimal,
    val price6Months: BigDecimal,
    val price12Months: BigDecimal,
    val setup1Month: BigDecimal = BigDecimal.ZERO,
    val setup6Months: BigDecimal = BigDecimal.ZERO,
    val setup12Months: BigDecimal = BigDecimal.ZERO,
    val contaboPlanId: String,
    val contaboCostPrice: BigDecimal? = null,
    val regions: List<String> = emptyList(),
    val availableAddons: List<String> = emptyList(),
    val storageTB: Double? = null,
    val sortOrder: Int = 0,
    val status: String = "ACTIVE",
)

data class UpdatePlanDto(
    val name: String? = null,
    val slug: String? = null,
    val productType: String? = null,
    val description: String? = null,
    val cpu: Int? = null, val ram: Int? = null, val disk: Int? = null,
    val diskType: String? = null,
    val bandwidth: Int? = null, val portSpeed: Int? = null,
    val snapshots: Int? = null,
    val priceMonthly: BigDecimal? = null,
    val price1Month: BigDecimal? = null,
    val price6Months: BigDecimal? = null,
    val price12Months: BigDecimal? = null,
    val setup1Month: BigDecimal? = null,
    val setup6Months: BigDecimal? = null,
    val setup12Months: BigDecimal? = null,
    val contaboPlanId: String? = null,
    val contaboCostPrice: BigDecimal? = null,
    val regions: List<String>? = null,
    val availableAddons: List<String>? = null,
    val storageTB: Double? = null,
    val sortOrder: Int? = null,
    val status: String? = null,
)

// ─── Addon Catalog DTOs ─────────────────────────────────────────

data class CreateAddonCatalogDto(
    val id: String,
    val category: String,
    val label: String,
    val contaboValue: String? = null,
    val billingType: String = "monthly_recurring",
    val isDefault: Boolean = false,
    val sortOrder: Int = 0,
)

data class UpdateAddonCatalogDto(
    val category: String? = null,
    val label: String? = null,
    val contaboValue: String? = null,
    val billingType: String? = null,
    val isDefault: Boolean? = null,
    val sortOrder: Int? = null,
)

// ─── Plan Addon Management DTOs ────────────────────────────────

data class UpdatePlanAddonsDto(
    val addons: List<com.valoracloud.api.common.config.PlanAddon>,
)

data class UpdateSingleAddonPriceDto(
    val priceMonthly: Double? = null,
    val regionPrices: Map<String, Double>? = null,
)

// ─── TLD Pricing DTOs ──────────────────────────────────────────
data class CreateTldPricingDto(
    val tld: String,
    val registrationPrice: BigDecimal,
    val renewalPrice: BigDecimal,
    val transferPrice: BigDecimal,
    val available: Boolean = true,
)

data class UpdateTldPricingDto(
    val registrationPrice: BigDecimal? = null,
    val renewalPrice: BigDecimal? = null,
    val transferPrice: BigDecimal? = null,
    val available: Boolean? = null,
)

// ─── Notification DTOs ─────────────────────────────────────────
data class SendMaintenanceDto(
    val region: String,
    val window: String,
    val localTime: String,
    val duration: String,
    val impact: String,
    val reference: String,
    val hostname: String = "",
    val userId: String? = null,
)

data class SendIncidentDto(
    val region: String,
    val startedAt: String,
    val services: String,
    val reference: String,
    val userId: String? = null,
)

// ─── Nameservers update DTO ────────────────────────────────────
data class NameserverEntry(val name: String, val ip: String? = null)
data class UpdateNameserversDto(val nameservers: List<NameserverEntry>)

// ─── Other admin action DTOs ───────────────────────────────────
data class ChangeServerPasswordDto(val password: String)
data class CreateSnapshotDto(val name: String, val description: String? = null)
data class UpdateSnapshotDto(val name: String? = null, val description: String? = null)
data class ReinstallServerDto(val imageId: String)
data class CancelDomainDto(val cancelDate: String? = null)
data class CancelStorageDto(val cancelDate: String? = null)
data class UpgradeStorageDto(val totalPurchasedSpaceTB: Double? = null, val autoScaling: Map<String, Any>? = null)

// ─── Import user / impersonation ───────────────────────────────
data class ImpersonateResponse(
    val accessToken: String,
    val expiresIn: Long = 3600,
    val impersonating: Map<String, String>,
)
