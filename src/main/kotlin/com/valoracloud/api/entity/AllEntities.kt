package com.valoracloud.api.entity

import com.valoracloud.api.common.model.*
import com.valoracloud.api.cuid
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.SQLRestriction
import org.hibernate.type.SqlTypes
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener

// ─────────────────────────────────────────────────────────
// Base
// ─────────────────────────────────────────────────────────
@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseEntity {
    @CreatedDate
    @Column(updatable = false, nullable = false)
    lateinit var createdAt: Instant
        protected set
    @LastModifiedDate
    @Column(nullable = false)
    lateinit var updatedAt: Instant
        protected set
}

// ─────────────────────────────────────────────────────────
// User & Auth
// ─────────────────────────────────────────────────────────
@Entity
@Table(name = "users")
@SQLRestriction("deleted_at IS NULL")
class User(
        @Id var id: String = cuid(),
        @Column(unique = true, nullable = false) var email: String = "",
        @Column(nullable = false) var password: String = "",
        var firstName: String = "",
        var lastName: String = "",
        @Enumerated(EnumType.STRING) var role: Role = Role.CLIENT,
        @Enumerated(EnumType.STRING) var status: UserStatus = UserStatus.ACTIVE,
        var emailVerified: Boolean = false,
        var language: String = "en",
        var deletedAt: Instant? = null,
) : BaseEntity()

@Entity
@Table(
        name = "refresh_tokens",
        indexes = [Index(columnList = "user_id"), Index(columnList = "token")]
)
class RefreshToken(
        @Id var id: String = cuid(),
        @Column(unique = true) var token: String = "",
        @Column(name = "user_id", nullable = false) var userId: String = "",
        @Column(nullable = false) var expiresAt: Instant = Instant.now(),
        var revokedAt: Instant? = null,
) : BaseEntity()

// ─────────────────────────────────────────────────────────
// Plan
// ─────────────────────────────────────────────────────────
@Entity
@Table(name = "plans", indexes = [Index(columnList = "productType")])
class Plan(
        @Id var id: String = cuid(),
        @Column(nullable = false) var name: String = "",
        @Column(nullable = false, unique = true) var slug: String = "",
        @Enumerated(EnumType.STRING)
        @Column(nullable = false)
        var productType: ProductType = ProductType.CLOUD_VPS,
        var description: String? = null,
        var cpu: Int = 1,
        var ram: Int = 1,
        var disk: Int = 25,
        var diskType: String = "NVMe",
        var bandwidth: Int = 1,
        var portSpeed: Int? = null,
        var snapshots: Int = 0,
        @Column(name = "price1_month", precision = 10, scale = 2)
        var price1Month: BigDecimal = BigDecimal.ZERO,
        @Column(name = "price6_months", precision = 10, scale = 2)
        var price6Months: BigDecimal = BigDecimal.ZERO,
        @Column(name = "price12_months", precision = 10, scale = 2)
        var price12Months: BigDecimal = BigDecimal.ZERO,
        @Column(name = "setup1_month", precision = 10, scale = 2)
        var setup1Month: BigDecimal = BigDecimal.ZERO,
        @Column(name = "setup6_months", precision = 10, scale = 2)
        var setup6Months: BigDecimal = BigDecimal.ZERO,
        @Column(name = "setup12_months", precision = 10, scale = 2)
        var setup12Months: BigDecimal = BigDecimal.ZERO,
        @Column(precision = 10, scale = 2) var priceMonthly: BigDecimal = BigDecimal.ZERO,
        var contaboPlanId: String = "",
        @Column(precision = 10, scale = 2) var contaboCostPrice: BigDecimal? = null,
        @JdbcTypeCode(SqlTypes.JSON) var regions: List<String> = emptyList(),
        @JdbcTypeCode(SqlTypes.JSON) var availableAddons: List<String> = emptyList(),
        @Column(name = "storage_tb") var storageTB: Double? = null,
        var sortOrder: Int = 0,
        @Enumerated(EnumType.STRING) var status: PlanStatus = PlanStatus.ACTIVE,
) : BaseEntity()

// ─────────────────────────────────────────────────────────
// Order
// ─────────────────────────────────────────────────────────
@Entity
@Table(name = "orders", indexes = [Index(columnList = "user_id"), Index(columnList = "status")])
class Order(
        @Id var id: String = cuid(),
        @Column(name = "user_id", nullable = false) var userId: String = "",
        @Column(name = "plan_id") var planId: String? = null,
        @Enumerated(EnumType.STRING) var serviceType: ServiceType = ServiceType.COMPUTE,
        @Enumerated(EnumType.STRING) var status: OrderStatus = OrderStatus.PENDING_PAYMENT,
        @Enumerated(EnumType.STRING) var paymentMethod: PaymentMethod = PaymentMethod.STRIPE,
        var stripePaymentId: String? = null,
        var cryptoPaymentId: String? = null,
        var cryptoCurrency: String? = null,
        var billingCycle: Int = 1,
        @Column(precision = 10, scale = 2) var basePrice: BigDecimal = BigDecimal.ZERO,
        @Column(precision = 10, scale = 2) var addonsPrice: BigDecimal = BigDecimal.ZERO,
        @Column(precision = 10, scale = 2) var setupFee: BigDecimal = BigDecimal.ZERO,
        @Column(precision = 10, scale = 2) var totalAmount: BigDecimal = BigDecimal.ZERO,
        var region: String = "EU",
        var os: String = "ubuntu-24.04",
        @JdbcTypeCode(SqlTypes.JSON) var addons: List<String> = emptyList(),
        var sshUser: String = "root",
        var hostname: String? = null,
        var rootPassword: String? = null,
) : BaseEntity()

// ─────────────────────────────────────────────────────────
// Server
// ─────────────────────────────────────────────────────────
@Entity
@Table(name = "servers", indexes = [Index(columnList = "user_id"), Index(columnList = "status")])
class Server(
        @Id var id: String = cuid(),
        @Column(name = "user_id", nullable = false) var userId: String = "",
        @Column(name = "order_id", unique = true, nullable = false) var orderId: String = "",
        @Column(unique = true, nullable = false) var contaboInstanceId: String = "",
        @Enumerated(EnumType.STRING) var status: ServerStatus = ServerStatus.PROVISIONING,
        var hostname: String = "",
        var ipAddress: String? = null,
        var sshUser: String = "root",
        @Column(nullable = false) var rootPassword: String = "",
        var os: String = "",
        var region: String = "EU",
        @JdbcTypeCode(SqlTypes.JSON) var contaboData: Map<String, Any>? = null,
        var provisionedAt: Instant? = null,
        var expiresAt: Instant? = null,
) : BaseEntity()

@Entity
@Table(name = "provisioning_logs", indexes = [Index(columnList = "server_id")])
class ProvisioningLog(
        @Id var id: String = cuid(),
        @Column(name = "server_id", nullable = false) var serverId: String = "",
        var step: String = "",
        var status: String = "",
        var message: String? = null,
        var createdAt: Instant = Instant.now(),
)

// ─────────────────────────────────────────────────────────
// Invoice
// ─────────────────────────────────────────────────────────
@Entity
@Table(name = "invoices", indexes = [Index(columnList = "user_id"), Index(columnList = "order_id")])
class Invoice(
        @Id var id: String = cuid(),
        @Column(name = "user_id", nullable = false) var userId: String = "",
        @Column(name = "order_id", nullable = false) var orderId: String = "",
        @Column(precision = 10, scale = 2) var amount: BigDecimal = BigDecimal.ZERO,
        var currency: String = "USD",
        @Enumerated(EnumType.STRING) var paymentMethod: PaymentMethod = PaymentMethod.STRIPE,
        var stripeInvoiceId: String? = null,
        var cryptoTxId: String? = null,
        var cryptoCurrency: String? = null,
        var cryptoAmount: String? = null,
        var paidAt: Instant? = null,
        var createdAt: Instant = Instant.now(),
)

@Entity
@Table(name = "webhook_events", indexes = [Index(columnList = "externalId")])
class WebhookEvent(
        @Id var id: String = cuid(),
        @Column(unique = true) var stripeEventId: String? = null,
        var externalId: String? = null,
        var eventSource: String = "stripe",
        var eventType: String = "",
        var processed: Boolean = false,
        var createdAt: Instant = Instant.now(),
)

// ─────────────────────────────────────────────────────────
// Object Storage
// ─────────────────────────────────────────────────────────
@Entity
@Table(
        name = "object_storages",
        indexes = [Index(columnList = "user_id"), Index(columnList = "status")]
)
class ObjectStorage(
        @Id var id: String = cuid(),
        @Column(name = "user_id", nullable = false) var userId: String = "",
        @Column(name = "order_id", unique = true, nullable = false) var orderId: String = "",
        @Column(unique = true) var contaboStorageId: String = "",
        @Enumerated(EnumType.STRING)
        var status: ObjectStorageStatus = ObjectStorageStatus.PROVISIONING,
        var displayName: String? = null,
        var region: String = "EU",
        var totalPurchasedSpaceTB: Double = 0.25,
        var usedSpaceTB: Double? = 0.0,
        @JdbcTypeCode(SqlTypes.JSON) var autoScaling: Map<String, Any>? = null,
        var s3Endpoint: String? = null,
        var s3AccessKey: String? = null,
        var s3SecretKey: String? = null,
        var provisionedAt: Instant? = null,
        var expiresAt: Instant? = null,
) : BaseEntity()

// ─────────────────────────────────────────────────────────
// Domain
// ─────────────────────────────────────────────────────────
@Entity
@Table(name = "domain_handles", indexes = [Index(columnList = "user_id")])
class DomainHandle(
        @Id var id: String = cuid(),
        @Column(name = "user_id", nullable = false) var userId: String = "",
        var contaboHandleId: String? = null,
        var handleType: String = "", // person | organization
        var firstName: String = "",
        var lastName: String = "",
        var organization: String? = null,
        var email: String = "",
        var gender: String? = null,
        @JdbcTypeCode(SqlTypes.JSON) var birthInfo: Map<String, Any>? = null,
        @JdbcTypeCode(SqlTypes.JSON) var address: Map<String, Any> = emptyMap(),
        @JdbcTypeCode(SqlTypes.JSON) var phone: Map<String, Any> = emptyMap(),
        var fax: String? = null,
) : BaseEntity()

@Entity
@Table(name = "domain_tld_pricings")
class DomainTldPricing(
        @Id var id: String = cuid(),
        @Column(unique = true) var tld: String = "",
        @Column(precision = 10, scale = 2) var registrationPrice: BigDecimal = BigDecimal.ZERO,
        @Column(precision = 10, scale = 2) var renewalPrice: BigDecimal = BigDecimal.ZERO,
        @Column(precision = 10, scale = 2) var transferPrice: BigDecimal = BigDecimal.ZERO,
        var available: Boolean = true,
) : BaseEntity()

@Entity
@Table(
        name = "domains",
        indexes =
                [
                        Index(columnList = "user_id"),
                        Index(columnList = "status"),
                        Index(columnList = "domainName")]
)
class Domain(
        @Id var id: String = cuid(),
        @Column(name = "user_id", nullable = false) var userId: String = "",
        @Column(name = "order_id", unique = true, nullable = false) var orderId: String = "",
        @Column(name = "tld_pricing_id", nullable = false) var tldPricingId: String = "",
        @Column(unique = true) var domainName: String = "",
        var authCode: String? = null,
        @Enumerated(EnumType.STRING) var status: DomainStatus = DomainStatus.PENDING,
        @Column(name = "owner_handle_id", nullable = false) var ownerHandleId: String = "",
        @Column(name = "admin_handle_id", nullable = false) var adminHandleId: String = "",
        @Column(name = "tech_handle_id", nullable = false) var techHandleId: String = "",
        @Column(name = "zone_handle_id", nullable = false) var zoneHandleId: String = "",
        @JdbcTypeCode(SqlTypes.JSON) var nameservers: List<Map<String, Any>> = emptyList(),
        var autoRenew: Boolean = true,
        var whoisPrivacy: Boolean = false,
        var registeredAt: Instant? = null,
        var expiresAt: Instant? = null,
) : BaseEntity()

// ─────────────────────────────────────────────────────────
// Snapshot
// ─────────────────────────────────────────────────────────
@Entity
@Table(
        name = "snapshots",
        indexes = [Index(columnList = "server_id"), Index(columnList = "contaboSnapshotId")]
)
class Snapshot(
        @Id var id: String = cuid(),
        @Column(name = "server_id", nullable = false) var serverId: String = "",
        @Column(unique = true) var contaboSnapshotId: String = "",
        var name: String = "",
        var description: String? = null,
) : BaseEntity()

// ─────────────────────────────────────────────────────────
// Private Network
// ─────────────────────────────────────────────────────────
@Entity
@Table(
        name = "private_networks",
        indexes = [Index(columnList = "user_id"), Index(columnList = "contaboNetworkId")]
)
class PrivateNetwork(
        @Id var id: String = cuid(),
        @Column(name = "user_id", nullable = false) var userId: String = "",
        @Column(unique = true) var contaboNetworkId: String = "",
        var name: String = "",
        var description: String? = null,
        var region: String = "EU",
        var dataCenter: String? = null,
        var cidr: String? = null,
) : BaseEntity()

@Entity
@Table(
        name = "private_network_assignments",
        indexes = [Index(columnList = "private_network_id"), Index(columnList = "server_id")]
)
// (unique constraint merged via @Table)
class PrivateNetworkAssignment(
        @Id var id: String = cuid(),
        @Column(name = "private_network_id", nullable = false) var privateNetworkId: String = "",
        @Column(name = "server_id", nullable = false) var serverId: String = "",
        var ipAddress: String? = null,
        var createdAt: Instant = Instant.now(),
)

// ─────────────────────────────────────────────────────────
// Firewall
// ─────────────────────────────────────────────────────────
@Entity
@Table(
        name = "firewalls",
        indexes = [Index(columnList = "user_id"), Index(columnList = "contaboFirewallId")]
)
class Firewall(
        @Id var id: String = cuid(),
        @Column(name = "user_id", nullable = false) var userId: String = "",
        @Column(unique = true) var contaboFirewallId: String = "",
        var name: String = "",
        var description: String? = null,
        var status: String = "active",
) : BaseEntity()

@Entity
@Table(name = "firewall_rules", indexes = [Index(columnList = "firewall_id")])
class FirewallRule(
        @Id var id: String = cuid(),
        @Column(name = "firewall_id", nullable = false) var firewallId: String = "",
        var protocol: String = "",
        var port: Int? = null,
        var portRange: String? = null,
        var sourceIp: String? = null,
        var sourceNet: String? = null,
        var action: String = "allow",
        var createdAt: Instant = Instant.now(),
)

@Entity
@Table(
        name = "firewall_assignments",
        indexes = [Index(columnList = "firewall_id"), Index(columnList = "server_id")]
)
// (unique constraint merged via @Table)
class FirewallAssignment(
        @Id var id: String = cuid(),
        @Column(name = "firewall_id", nullable = false) var firewallId: String = "",
        @Column(name = "server_id", nullable = false) var serverId: String = "",
        var createdAt: Instant = Instant.now(),
)

// ─────────────────────────────────────────────────────────
// VIP
// ─────────────────────────────────────────────────────────
@Entity
@Table(name = "vips", indexes = [Index(columnList = "user_id"), Index(columnList = "ip")])
class Vip(
        @Id var id: String = cuid(),
        @Column(name = "user_id", nullable = false) var userId: String = "",
        @Column(unique = true) var ip: String = "",
        var resourceId: String? = null,
        var resourceType: String? = null,
        var ipVersion: String = "v4",
        var type: String = "",
        var dataCenter: String? = null,
        var region: String = "EU",
) : BaseEntity()

// ─────────────────────────────────────────────────────────
// Secret & Tag
// ─────────────────────────────────────────────────────────
@Entity
@Table(name = "secrets", indexes = [Index(columnList = "user_id")])
class Secret(
        @Id var id: String = cuid(),
        @Column(name = "user_id", nullable = false) var userId: String = "",
        @Column(unique = true) var contaboId: Int = 0,
        var name: String = "",
        var type: String = "", // ssh | password
) : BaseEntity()

@Entity
@Table(name = "tags", indexes = [Index(columnList = "user_id")])
class Tag(
        @Id var id: String = cuid(),
        @Column(name = "user_id", nullable = false) var userId: String = "",
        @Column(unique = true) var contaboId: Int = 0,
        var name: String = "",
        var color: String? = null,
) : BaseEntity()

// ─────────────────────────────────────────────────────────
// Monitoring
// ─────────────────────────────────────────────────────────
@Entity
@Table(name = "server_monitors", indexes = [Index(columnList = "isActive")])
class ServerMonitor(
        @Id var id: String = cuid(),
        @Column(name = "server_id", unique = true, nullable = false) var serverId: String = "",
        var isActive: Boolean = true,
        var protocol: String = "https",
        var checkUrl: String? = null,
        var checkPort: Int = 80,
        var checkIntervalSeconds: Int = 60,
        var agentPort: Int? = null,
        var agentSecret: String? = null,
        var alertThresholdPingMs: Int = 200,
        var alertThresholdCpuPct: Int = 85,
        var alertThresholdRamPct: Int = 85,
        var alertThresholdDiskPct: Int = 90,
        var notifyEmail: String? = null,
        var notifyTelegramChatId: String? = null,
        var notes: String? = null,
) : BaseEntity()

@Entity
@Table(name = "server_checks", indexes = [Index(columnList = "monitor_id, checked_at")])
class ServerCheck(
        @Id var id: String = cuid(),
        @Column(name = "monitor_id", nullable = false) var monitorId: String = "",
        var checkedAt: Instant = Instant.now(),
        @Enumerated(EnumType.STRING) var status: MonitorStatus = MonitorStatus.UP,
        var pingMs: Int? = null,
        var httpStatusCode: Int? = null,
        var httpResponseTimeMs: Int? = null,
        var httpResponseBodySnippet: String? = null,
        var tcpOpen: Boolean? = null,
        var sslValid: Boolean? = null,
        var sslDaysUntilExpiry: Int? = null,
        var sslIssuer: String? = null,
        var dnsResolved: Boolean? = null,
        var dnsIpResolved: String? = null,
        @Column(precision = 5, scale = 2) var cpuPercent: BigDecimal? = null,
        @Column(precision = 5, scale = 2) var ramPercent: BigDecimal? = null,
        var ramUsedMb: Int? = null,
        var ramTotalMb: Int? = null,
        @Column(precision = 5, scale = 2) var diskPercent: BigDecimal? = null,
        @Column(precision = 10, scale = 2) var diskUsedGb: BigDecimal? = null,
        @Column(precision = 10, scale = 2) var diskTotalGb: BigDecimal? = null,
        @Column(precision = 5, scale = 2) var loadAvg1m: BigDecimal? = null,
        @Column(precision = 5, scale = 2) var loadAvg5m: BigDecimal? = null,
        @Column(precision = 5, scale = 2) var loadAvg15m: BigDecimal? = null,
        @Column(precision = 10, scale = 2) var networkInMbps: BigDecimal? = null,
        @Column(precision = 10, scale = 2) var networkOutMbps: BigDecimal? = null,
        var openConnections: Int? = null,
        var processesCount: Int? = null,
        var errorMessage: String? = null,
        var checkDurationMs: Int? = null,
        var checkerNode: String = "primary",
)

@Entity
@Table(
        name = "monitor_alerts",
        indexes = [Index(columnList = "monitor_id, is_resolved, triggered_at")]
)
class MonitorAlert(
        @Id var id: String = cuid(),
        @Column(name = "monitor_id", nullable = false) var monitorId: String = "",
        @Column(name = "check_id") var checkId: String? = null,
        @Enumerated(EnumType.STRING) var type: MonitorAlertType = MonitorAlertType.DOWN,
        @Enumerated(EnumType.STRING)
        var severity: MonitorAlertSeverity = MonitorAlertSeverity.CRITICAL,
        var message: String = "",
        @Column(precision = 10, scale = 2) var value: BigDecimal? = null,
        @Column(precision = 10, scale = 2) var threshold: BigDecimal? = null,
        var triggeredAt: Instant = Instant.now(),
        var resolvedAt: Instant? = null,
        var isResolved: Boolean = false,
        var notifiedEmail: Boolean = false,
        var notifiedTelegram: Boolean = false,
        var ackAt: Instant? = null,
        var ackNote: String? = null,
)

@Entity
@Table(
        name = "uptime_dailies",
        uniqueConstraints = [UniqueConstraint(columnNames = ["monitor_id", "date"])]
)
class UptimeDaily(
        @Id var id: String = cuid(),
        @Column(name = "monitor_id", nullable = false) var monitorId: String = "",
        @Column(columnDefinition = "DATE") var date: LocalDate = LocalDate.now(),
        var totalChecks: Int = 0,
        var upChecks: Int = 0,
        var downChecks: Int = 0,
        var degradedChecks: Int = 0,
        @Column(precision = 5, scale = 2) var uptimePercent: BigDecimal? = null,
        @Column(precision = 8, scale = 2) var avgPingMs: BigDecimal? = null,
        @Column(precision = 5, scale = 2) var avgCpuPercent: BigDecimal? = null,
        @Column(precision = 5, scale = 2) var avgRamPercent: BigDecimal? = null,
        var maxPingMs: Int? = null,
        @Column(precision = 5, scale = 2) var maxCpuPercent: BigDecimal? = null,
        var incidentsCount: Int = 0,
        var totalDowntimeSeconds: Int = 0,
        var firstDownAt: Instant? = null,
)

@Entity
@Table(name = "maintenance_windows", indexes = [Index(columnList = "monitor_id")])
class MaintenanceWindow(
        @Id var id: String = cuid(),
        @Column(name = "monitor_id", nullable = false) var monitorId: String = "",
        var title: String = "",
        var startsAt: Instant = Instant.now(),
        var endsAt: Instant = Instant.now(),
        var suppressAlerts: Boolean = true,
        var createdBy: String? = null,
        var createdAt: Instant = Instant.now(),
)

// ─────────────────────────────────────────────────────────
// Email Log
// ─────────────────────────────────────────────────────────
@Entity
@Table(
        name = "email_logs",
        indexes =
                [
                        Index(columnList = "user_id"),
                        Index(columnList = "status"),
                        Index(columnList = "templateName"),
                        Index(columnList = "sent_at")]
)
class EmailLog(
        @Id var id: String = cuid(),
        @Column(name = "user_id") var userId: String? = null,
        var to: String = "",
        var templateName: String = "",
        var subject: String = "",
        var language: String = "en",
        var status: String = "",
        var triggeredBy: String = "",
        @JdbcTypeCode(SqlTypes.JSON) var variables: Map<String, Any> = emptyMap(),
        var renderedHtml: String = "",
        var errorMessage: String? = null,
        var sentAt: Instant = Instant.now(),
)
