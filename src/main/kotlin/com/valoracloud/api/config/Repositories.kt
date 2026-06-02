package com.valoracloud.api.config

import com.valoracloud.api.entity.*
import java.time.Instant
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

// ─── User ────────────────────────────────────────────────
@Repository
interface UserRepository : JpaRepository<User, String> {
    fun findByEmail(email: String): User?
    fun findByEmailAndDeletedAtIsNull(email: String): User?
    fun existsByEmail(email: String): Boolean
}

@Repository
interface RefreshTokenRepository : JpaRepository<RefreshToken, String> {
    fun findByToken(token: String): RefreshToken?
    fun findByUserIdAndRevokedAtIsNull(userId: String): List<RefreshToken>
    fun deleteByUserId(userId: String)
}

// ─── Plan ────────────────────────────────────────────────
@Repository
interface PlanRepository : JpaRepository<Plan, String> {
    fun findBySlug(slug: String): Plan?
    fun findByProductTypeAndStatus(
            productType: com.valoracloud.api.common.model.ProductType,
            status: com.valoracloud.api.common.model.PlanStatus
    ): List<Plan>
    fun findByStatusOrderBySortOrderAsc(
            status: com.valoracloud.api.common.model.PlanStatus
    ): List<Plan>
}

// ─── Order ───────────────────────────────────────────────
@Repository
interface OrderRepository : JpaRepository<Order, String> {
    fun findByUserIdOrderByCreatedAtDesc(userId: String): List<Order>
    fun findByStatus(status: String): List<Order>
    fun findByStripePaymentId(stripePaymentId: String): Order?
    fun findByCryptoPaymentId(cryptoPaymentId: String): Order?
}

// ─── Server ──────────────────────────────────────────────
@Repository
interface ServerRepository : JpaRepository<Server, String> {
    fun findByUserIdOrderByCreatedAtDesc(userId: String): List<Server>
    fun findByUserIdAndId(userId: String, id: String): Server?
    fun findByContaboInstanceId(contaboInstanceId: String): Server?
    fun findByOrderId(orderId: String): Server?
    fun findByStatusAndExpiresAtBefore(status: String, before: Instant): List<Server>
}

@Repository
interface ProvisioningLogRepository : JpaRepository<ProvisioningLog, String> {
    fun findByServerIdOrderByCreatedAtAsc(serverId: String): List<ProvisioningLog>
}

// ─── Invoice ─────────────────────────────────────────────
@Repository
interface InvoiceRepository : JpaRepository<Invoice, String> {
    fun findByUserIdOrderByCreatedAtDesc(userId: String): List<Invoice>
    fun findByOrderId(orderId: String): List<Invoice>
}

@Repository
interface WebhookEventRepository : JpaRepository<WebhookEvent, String> {
    fun findByStripeEventId(stripeEventId: String): WebhookEvent?
    fun findByExternalId(externalId: String): List<WebhookEvent>
}

// ─── Object Storage ──────────────────────────────────────
@Repository
interface ObjectStorageRepository : JpaRepository<ObjectStorage, String> {
    fun findByUserIdOrderByCreatedAtDesc(userId: String): List<ObjectStorage>
    fun findByUserIdAndId(userId: String, id: String): ObjectStorage?
    fun findByContaboStorageId(contaboStorageId: String): ObjectStorage?
    fun findByOrderId(orderId: String): ObjectStorage?
}

// ─── Domain ──────────────────────────────────────────────
@Repository
interface DomainRepository : JpaRepository<Domain, String> {
    fun findByUserIdOrderByCreatedAtDesc(userId: String): List<Domain>
    fun findByDomainName(domainName: String): Domain?
    fun findByOrderId(orderId: String): Domain?
}

@Repository
interface DomainHandleRepository : JpaRepository<DomainHandle, String> {
    fun findByUserId(userId: String): List<DomainHandle>
}

@Repository
interface DomainTldPricingRepository : JpaRepository<DomainTldPricing, String> {
    fun findByTld(tld: String): DomainTldPricing?
    fun findByAvailableTrue(): List<DomainTldPricing>
}

// ─── Snapshot ────────────────────────────────────────────
@Repository
interface SnapshotRepository : JpaRepository<Snapshot, String> {
    fun findByServerId(serverId: String): List<Snapshot>
    fun findByContaboSnapshotId(contaboSnapshotId: String): Snapshot?
}

// ─── Private Network ─────────────────────────────────────
@Repository
interface PrivateNetworkRepository : JpaRepository<PrivateNetwork, String> {
    fun findByUserId(userId: String): List<PrivateNetwork>
    fun findByContaboNetworkId(contaboNetworkId: String): PrivateNetwork?
}

@Repository
interface PrivateNetworkAssignmentRepository : JpaRepository<PrivateNetworkAssignment, String> {
    fun findByPrivateNetworkId(privateNetworkId: String): List<PrivateNetworkAssignment>
    fun findByServerId(serverId: String): List<PrivateNetworkAssignment>
    fun findByPrivateNetworkIdAndServerId(
            networkId: String,
            serverId: String
    ): PrivateNetworkAssignment?
}

// ─── Firewall ────────────────────────────────────────────
@Repository
interface FirewallRepository : JpaRepository<Firewall, String> {
    fun findByUserId(userId: String): List<Firewall>
    fun findByContaboFirewallId(contaboFirewallId: String): Firewall?
}

@Repository
interface FirewallRuleRepository : JpaRepository<FirewallRule, String> {
    fun findByFirewallId(firewallId: String): List<FirewallRule>
}

@Repository
interface FirewallAssignmentRepository : JpaRepository<FirewallAssignment, String> {
    fun findByFirewallId(firewallId: String): List<FirewallAssignment>
    fun findByServerId(serverId: String): List<FirewallAssignment>
    fun findByFirewallIdAndServerId(firewallId: String, serverId: String): FirewallAssignment?
}

// ─── VIP ─────────────────────────────────────────────────
@Repository
interface VipRepository : JpaRepository<Vip, String> {
    fun findByUserId(userId: String): List<Vip>
    fun findByIp(ip: String): Vip?
}

// ─── Secret & Tag ────────────────────────────────────────
@Repository
interface SecretRepository : JpaRepository<Secret, String> {
    fun findByUserId(userId: String): List<Secret>
    fun findByContaboId(contaboId: Int): Secret?
}

@Repository
interface TagRepository : JpaRepository<Tag, String> {
    fun findByUserId(userId: String): List<Tag>
    fun findByContaboId(contaboId: Int): Tag?
}

// ─── Monitoring ──────────────────────────────────────────
@Repository
interface ServerMonitorRepository : JpaRepository<ServerMonitor, String> {
    fun findByServerId(serverId: String): ServerMonitor?
    fun findByIsActiveTrue(): List<ServerMonitor>
    fun findByIsActiveTrueAndCheckIntervalSecondsLessThanEqual(
            checkIntervalSeconds: Int
    ): List<ServerMonitor>
}

@Repository
interface ServerCheckRepository : JpaRepository<ServerCheck, String> {
    fun findByMonitorIdOrderByCheckedAtDesc(monitorId: String): List<ServerCheck>
    fun findByMonitorIdAndCheckedAtBetween(
            monitorId: String,
            start: Instant,
            end: Instant
    ): List<ServerCheck>
}

@Repository
interface MonitorAlertRepository : JpaRepository<MonitorAlert, String> {
    fun findByMonitorIdAndIsResolvedOrderByTriggeredAtDesc(
            monitorId: String,
            isResolved: Boolean
    ): List<MonitorAlert>
    fun findByMonitorIdOrderByTriggeredAtDesc(monitorId: String): List<MonitorAlert>
    fun findByIsResolvedAndNotifiedEmail(
            isResolved: Boolean,
            notifiedEmail: Boolean
    ): List<MonitorAlert>
}

@Repository
interface UptimeDailyRepository : JpaRepository<UptimeDaily, String> {
    fun findByMonitorIdAndDate(monitorId: String, date: java.time.LocalDate): UptimeDaily?
    fun findByMonitorIdOrderByDateDesc(monitorId: String): List<UptimeDaily>
}

@Repository
interface MaintenanceWindowRepository : JpaRepository<MaintenanceWindow, String> {
    fun findByMonitorId(monitorId: String): List<MaintenanceWindow>
    fun findByMonitorIdAndStartsAtBeforeAndEndsAtAfter(
            monitorId: String,
            now: Instant,
            now2: Instant
    ): List<MaintenanceWindow>
}

// ─── Email Log ───────────────────────────────────────────
@Repository
interface EmailLogRepository : JpaRepository<EmailLog, String> {
    fun findByUserIdOrderBySentAtDesc(userId: String): List<EmailLog>
}
