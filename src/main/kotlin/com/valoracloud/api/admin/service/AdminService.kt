package com.valoracloud.api.admin.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.valoracloud.api.admin.dto.*
import com.valoracloud.api.common.config.PlanAddon
import com.valoracloud.api.auth.security.JwtProvider
import com.valoracloud.api.common.dto.PaginatedResponse
import com.valoracloud.api.common.dto.PaginationDto
import com.valoracloud.api.common.exceptions.BadRequestException
import com.valoracloud.api.common.exceptions.NotFoundException
import com.valoracloud.api.common.model.*
import com.valoracloud.api.common.utils.EncryptionUtil
import com.valoracloud.api.config.*
import com.valoracloud.api.contabo.*
import com.valoracloud.api.entity.*
import com.valoracloud.api.notifications.service.NotificationsService
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class AdminService(
        private val userRepo: UserRepository,
        private val orderRepo: OrderRepository,
        private val planRepo: PlanRepository,
        private val serverRepo: ServerRepository,
        private val invoiceRepo: InvoiceRepository,
        private val webhookEventRepo: WebhookEventRepository,
        private val emailLogRepo: EmailLogRepository,
        private val domainRepo: DomainRepository,
        private val objectStorageRepo: ObjectStorageRepository,
        private val domainTldPricingRepo: DomainTldPricingRepository,
        private val domainHandleRepo: DomainHandleRepository,
        private val firewallRepo: FirewallRepository,
        private val firewallRuleRepo: FirewallRuleRepository,
        private val firewallAssignmentRepo: FirewallAssignmentRepository,
        private val snapshotRepo: SnapshotRepository,
        private val privateNetworkRepo: PrivateNetworkRepository,
        private val privateNetworkAssignmentRepo: PrivateNetworkAssignmentRepository,
        private val vipRepo: VipRepository,
        private val secretRepo: SecretRepository,
        private val tagRepo: TagRepository,
        private val refreshTokenRepo: RefreshTokenRepository,
        private val provisioningLogRepo: ProvisioningLogRepository,
        private val addonCatalogRepo: AddonCatalogRepository,
        private val contabo: ContaboService,
        private val jwt: JwtProvider,
        private val notifications: NotificationsService,
        @Value("\${app.encryption-key}") private val encryptionKey: String,
        @Value("\${BRAND_DOMAIN:valoracloud.com}") private val brandDomain: String,
        private val mapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    // ════════════════════════════════════════════════════════════
    // Stats
    // ════════════════════════════════════════════════════════════

    fun getStats(period: StatsPeriodDto): AdminStatsDto {
        val totalUsers = userRepo.count()
        val deletedUsers = userRepo.findAll().count { it.deletedAt != null }
        val unverifiedUsers = userRepo.findAll().count { !it.emailVerified }
        val allServers = serverRepo.findAll()
        val totalServers = allServers.size.toLong()
        val serversByStatus =
                allServers.groupBy { it.status.name }.mapValues { it.value.size.toLong() }
        val allOrders = orderRepo.findAll()
        val totalOrders = allOrders.size.toLong()
        val ordersByStatus =
                allOrders.groupBy { it.status.name }.mapValues { it.value.size.toLong() }
        val paidOrders =
                allOrders.filter {
                    it.status == OrderStatus.PAID || it.status == OrderStatus.ACTIVE
                }
        val fromOrders = paidOrders.fold(BigDecimal.ZERO) { acc, o -> acc + o.totalAmount }
        val allInvoices = invoiceRepo.findAll()
        val totalInvoices = allInvoices.size.toLong()
        val fromInvoices =
                allInvoices.filter { it.paidAt != null }.fold(BigDecimal.ZERO) { acc, i ->
                    acc + i.amount
                }
        val activeSessions =
                refreshTokenRepo.findAll().count {
                    it.revokedAt == null && it.expiresAt > Instant.now()
                }
        val unprocessedWebhooks = webhookEventRepo.findAll().count { !it.processed }

        return AdminStatsDto(
                users = UsersStats(totalUsers, deletedUsers.toLong(), unverifiedUsers.toLong()),
                servers = ServersStats(totalServers, serversByStatus),
                orders = OrdersStats(totalOrders, ordersByStatus),
                revenue = RevenueStats(fromOrders, fromInvoices, totalInvoices),
                sessions = SessionsStats(activeSessions.toLong()),
                webhooks = WebhooksStats(unprocessedWebhooks.toLong()),
        )
    }

    fun getRevenueStats(dto: StatsPeriodDto): RevenueStatsDto {
        val days =
                when (dto.period) {
                    "7d" -> 7
                    "90d" -> 90
                    else -> 30
                }
        val since = Instant.now().minusSeconds(days * 86400L)
        val invoices =
                invoiceRepo
                        .findAll()
                        .filter { it.createdAt.isAfter(since) }
                        .groupBy { LocalDate.ofInstant(it.createdAt, java.time.ZoneOffset.UTC) }
                        .map { (date, invs) ->
                            RevenueSeriesEntry(
                                    date = date.format(DateTimeFormatter.ISO_LOCAL_DATE),
                                    revenue = invs.sumOf { it.amount.toDouble() }
                            )
                        }
                        .sortedBy { it.date }
        return RevenueStatsDto(period = dto.period, series = invoices)
    }

    fun getNewUsersStats(dto: StatsPeriodDto): NewUsersStatsDto {
        val days =
                when (dto.period) {
                    "7d" -> 7
                    "90d" -> 90
                    else -> 30
                }
        val since = Instant.now().minusSeconds(days * 86400L)
        val rows =
                userRepo.findAll()
                        .filter { it.deletedAt == null && it.createdAt.isAfter(since) }
                        .groupBy { LocalDate.ofInstant(it.createdAt, java.time.ZoneOffset.UTC) }
                        .map { (date, users) ->
                            NewUsersSeriesEntry(
                                    date.format(DateTimeFormatter.ISO_LOCAL_DATE),
                                    count = users.size
                            )
                        }
                        .sortedBy { it.date }
        return NewUsersStatsDto(period = dto.period, series = rows)
    }

    fun getServersByRegion(): List<RegionCount> =
            serverRepo.findAll().groupBy { it.region }.map {
                RegionCount(it.key, it.value.size.toLong())
            }

    fun getOrdersByStatus(): List<StatusCount> =
            orderRepo.findAll().groupBy { it.status.name }.map {
                StatusCount(it.key, it.value.size.toLong())
            }

    // ════════════════════════════════════════════════════════════
    // Users
    // ════════════════════════════════════════════════════════════

    fun listUsers(filter: AdminUsersFilterDto): PaginatedResponse<Map<String, Any?>> {
        val pageable = PageRequest.of((filter.page ?: 1) - 1, filter.limit ?: 20)
        var users: List<com.valoracloud.api.entity.User> = userRepo.findAll(pageable).content
        if (!(filter.includeDeleted ?: false)) {
            users = users.filter { it.deletedAt == null }
        }
        if (filter.role != null) users = users.filter { it.role.name == filter.role }
        if (filter.status != null) users = users.filter { it.status.name == filter.status }
        if (filter.emailVerified != null)
                users = users.filter { it.emailVerified == filter.emailVerified }
        if (!filter.search.isNullOrBlank()) {
            val q = filter.search.lowercase()
            users =
                    users.filter {
                        it.email.lowercase().contains(q) ||
                                it.firstName.lowercase().contains(q) ||
                                it.lastName.lowercase().contains(q)
                    }
        }
        val total = users.size.toLong()
        val data =
                users.map { u ->
                    mapOf<String, Any?>(
                            "id" to u.id,
                            "email" to u.email,
                            "firstName" to u.firstName,
                            "lastName" to u.lastName,
                            "role" to u.role.name,
                            "status" to u.status.name,
                            "emailVerified" to u.emailVerified,
                            "deletedAt" to u.deletedAt,
                            "createdAt" to u.createdAt,
                            "updatedAt" to u.updatedAt,
                    )
                }
        return PaginatedResponse(
                data,
                total,
                filter.page ?: 1,
                filter.limit ?: 20,
                ((total + filter.limit!! - 1) / filter.limit!!).toInt()
        )
    }

    fun getUserDetail(userId: String): Map<String, Any?> {
        val user = userRepo.findByIdOrNull(userId) ?: throw NotFoundException("User", userId)
        val servers = serverRepo.findByUserIdOrderByCreatedAtDesc(userId)
        val orders = orderRepo.findByUserIdOrderByCreatedAtDesc(userId)
        val invoices = invoiceRepo.findByUserIdOrderByCreatedAtDesc(userId)
        return mapOf(
                "id" to user.id,
                "email" to user.email,
                "firstName" to user.firstName,
                "lastName" to user.lastName,
                "role" to user.role.name,
                "status" to user.status.name,
                "emailVerified" to user.emailVerified,
                "deletedAt" to user.deletedAt,
                "createdAt" to user.createdAt,
                "updatedAt" to user.updatedAt,
                "servers" to
                        servers.map { s ->
                            mapOf(
                                    "id" to s.id,
                                    "hostname" to s.hostname,
                                    "ipAddress" to s.ipAddress,
                                    "status" to s.status.name,
                                    "os" to s.os,
                                    "region" to s.region,
                                    "contaboInstanceId" to s.contaboInstanceId,
                                    "provisionedAt" to s.provisionedAt,
                                    "expiresAt" to s.expiresAt,
                                    "createdAt" to s.createdAt
                            )
                        },
                "orders" to
                        orders.map { o ->
                            val plan = o.planId?.let { planRepo.findByIdOrNull(it) }
                            mapOf(
                                    "id" to o.id,
                                    "status" to o.status.name,
                                    "totalAmount" to o.totalAmount,
                                    "billingCycle" to o.billingCycle,
                                    "region" to o.region,
                                    "stripePaymentId" to o.stripePaymentId,
                                    "createdAt" to o.createdAt,
                                    "plan" to
                                            plan?.let {
                                                mapOf("name" to it.name, "slug" to it.slug)
                                            }
                            )
                        },
                "invoices" to
                        invoices.map { i ->
                            mapOf(
                                    "id" to i.id,
                                    "amount" to i.amount,
                                    "currency" to i.currency,
                                    "stripeInvoiceId" to i.stripeInvoiceId,
                                    "paidAt" to i.paidAt,
                                    "createdAt" to i.createdAt
                            )
                        },
        )
    }

    fun getUserSessions(userId: String) =
            refreshTokenRepo
                    .findByUserIdAndRevokedAtIsNull(userId)
                    .filter { it.expiresAt > Instant.now() }
                    .map {
                        mapOf(
                                "id" to it.id,
                                "createdAt" to it.createdAt,
                                "expiresAt" to it.expiresAt
                        )
                    }

    fun revokeUserSessions(userId: String): Map<String, Int> {
        val user = userRepo.findByIdOrNull(userId) ?: throw NotFoundException("User", userId)
        val tokens = refreshTokenRepo.findByUserIdAndRevokedAtIsNull(userId)
        tokens.forEach {
            it.revokedAt = Instant.now()
            refreshTokenRepo.save(it)
        }
        return mapOf("revokedCount" to tokens.size)
    }

    fun updateUserRole(userId: String, dto: UpdateUserRoleDto): Map<String, Any?> {
        val user = userRepo.findByIdOrNull(userId) ?: throw NotFoundException("User", userId)
        user.role = Role.valueOf(dto.role)
        val saved = userRepo.save(user)
        return mapOf(
                "id" to saved.id,
                "email" to saved.email,
                "role" to saved.role.name,
                "status" to saved.status.name,
                "updatedAt" to saved.updatedAt
        )
    }

    fun updateUserStatus(userId: String, dto: UpdateUserStatusDto): Map<String, Any?> {
        val user = userRepo.findByIdOrNull(userId) ?: throw NotFoundException("User", userId)
        user.status = UserStatus.valueOf(dto.status)
        val saved = userRepo.save(user)
        return mapOf(
                "id" to saved.id,
                "email" to saved.email,
                "role" to saved.role.name,
                "status" to saved.status.name,
                "updatedAt" to saved.updatedAt
        )
    }

    fun deleteUser(userId: String): Map<String, Any?> {
        val user = userRepo.findByIdOrNull(userId) ?: throw NotFoundException("User", userId)
        refreshTokenRepo.findByUserIdAndRevokedAtIsNull(userId).forEach {
            it.revokedAt = Instant.now()
            refreshTokenRepo.save(it)
        }
        user.deletedAt = Instant.now()
        val saved = userRepo.save(user)
        return mapOf("id" to saved.id, "email" to saved.email, "deletedAt" to saved.deletedAt)
    }

    fun restoreUser(userId: String): Map<String, Any?> {
        val user = userRepo.findByIdOrNull(userId) ?: throw NotFoundException("User", userId)
        user.deletedAt = null
        val saved = userRepo.save(user)
        return mapOf("id" to saved.id, "email" to saved.email, "deletedAt" to saved.deletedAt)
    }

    fun forceVerifyEmail(userId: String): Map<String, Any?> {
        val user = userRepo.findByIdOrNull(userId) ?: throw NotFoundException("User", userId)
        user.emailVerified = true
        val saved = userRepo.save(user)
        return mapOf(
                "id" to saved.id,
                "email" to saved.email,
                "emailVerified" to saved.emailVerified
        )
    }

    fun impersonateUser(userId: String): ImpersonateResponse {
        val user = userRepo.findByIdOrNull(userId) ?: throw NotFoundException("User", userId)
        val token = jwt.generateAccessToken(user.id, user.email, user.role.name)
        return ImpersonateResponse(
                accessToken = token,
                impersonating =
                        mapOf("id" to user.id, "email" to user.email, "role" to user.role.name),
        )
    }

    // ════════════════════════════════════════════════════════════
    // Servers
    // ════════════════════════════════════════════════════════════

    fun listServers(filter: AdminServersFilterDto): PaginatedResponse<Map<String, Any?>> {
        val pageable = PageRequest.of((filter.page ?: 1) - 1, filter.limit ?: 20)
        var servers = serverRepo.findAll(pageable).content
        if (filter.status != null) servers = servers.filter { it.status.name == filter.status }
        if (filter.userId != null) servers = servers.filter { it.userId == filter.userId }
        if (filter.region != null) servers = servers.filter { it.region == filter.region }
        val total = servers.size.toLong()
        val data =
                servers.map { s ->
                    val user = userRepo.findByIdOrNull(s.userId)
                    mapOf(
                            "id" to s.id,
                            "hostname" to s.hostname,
                            "ipAddress" to s.ipAddress,
                            "status" to s.status.name,
                            "os" to s.os,
                            "region" to s.region,
                            "contaboInstanceId" to s.contaboInstanceId,
                            "provisionedAt" to s.provisionedAt,
                            "expiresAt" to s.expiresAt,
                            "createdAt" to s.createdAt,
                            "user" to
                                    user?.let {
                                        mapOf(
                                                "id" to it.id,
                                                "email" to it.email,
                                                "firstName" to it.firstName,
                                                "lastName" to it.lastName
                                        )
                                    },
                    )
                }
        return PaginatedResponse(
                data,
                total,
                filter.page ?: 1,
                filter.limit ?: 20,
                ((total + filter.limit!! - 1) / filter.limit!!).toInt()
        )
    }

    fun getServerDetail(serverId: String): Map<String, Any?> {
        val server =
                serverRepo.findByIdOrNull(serverId) ?: throw NotFoundException("Server", serverId)
        val user = userRepo.findByIdOrNull(server.userId)
        val order = orderRepo.findByIdOrNull(server.orderId)
        val plan = order?.planId?.let { planRepo.findByIdOrNull(it) }
        val logs = provisioningLogRepo.findByServerIdOrderByCreatedAtAsc(serverId)
        return mapOf(
                "id" to server.id,
                "hostname" to server.hostname,
                "ipAddress" to server.ipAddress,
                "status" to server.status.name,
                "os" to server.os,
                "region" to server.region,
                "contaboInstanceId" to server.contaboInstanceId,
                "contaboData" to server.contaboData,
                "provisionedAt" to server.provisionedAt,
                "expiresAt" to server.expiresAt,
                "createdAt" to server.createdAt,
                "updatedAt" to server.updatedAt,
                "user" to
                        user?.let {
                            mapOf(
                                    "id" to it.id,
                                    "email" to it.email,
                                    "firstName" to it.firstName,
                                    "lastName" to it.lastName
                            )
                        },
                "order" to
                        order?.let {
                            mapOf(
                                    "id" to it.id,
                                    "status" to it.status.name,
                                    "totalAmount" to it.totalAmount,
                                    "billingCycle" to it.billingCycle,
                                    "stripePaymentId" to it.stripePaymentId,
                                    "createdAt" to it.createdAt,
                                    "plan" to
                                            plan?.let { p ->
                                                mapOf(
                                                        "name" to p.name,
                                                        "slug" to p.slug,
                                                        "cpu" to p.cpu,
                                                        "ram" to p.ram,
                                                        "disk" to p.disk
                                                )
                                            }
                            )
                        },
                "provisioningLogs" to
                        logs.map {
                            mapOf(
                                    "id" to it.id,
                                    "step" to it.step,
                                    "status" to it.status,
                                    "message" to it.message,
                                    "createdAt" to it.createdAt
                            )
                        },
        )
    }

    fun getServerCredentials(serverId: String): Map<String, Any?> {
        val server =
                serverRepo.findByIdOrNull(serverId) ?: throw NotFoundException("Server", serverId)
        return mapOf(
                "id" to server.id,
                "hostname" to server.hostname,
                "ipAddress" to server.ipAddress,
                "sshUser" to server.sshUser,
                "rootPassword" to EncryptionUtil.decrypt(server.rootPassword, encryptionKey),
        )
    }

    fun getServerConsole(serverId: String): Map<String, Any?> {
        val server =
                serverRepo.findByIdOrNull(serverId) ?: throw NotFoundException("Server", serverId)
        val vnc = contabo.getVncAccess(server.contaboInstanceId.toLong())
        return mapOf("hostname" to server.hostname, "ipAddress" to server.ipAddress, "vnc" to vnc)
    }

    fun adminStartServer(serverId: String): Map<String, Any?> {
        val server =
                serverRepo.findByIdOrNull(serverId) ?: throw NotFoundException("Server", serverId)
        contabo.startInstance(server.contaboInstanceId.toLong())
        server.status = ServerStatus.RUNNING
        val saved = serverRepo.save(server)
        return mapOf("id" to saved.id, "hostname" to saved.hostname, "status" to saved.status.name)
    }

    fun adminStopServer(serverId: String): Map<String, Any?> {
        val server =
                serverRepo.findByIdOrNull(serverId) ?: throw NotFoundException("Server", serverId)
        contabo.stopInstance(server.contaboInstanceId.toLong())
        server.status = ServerStatus.STOPPED
        val saved = serverRepo.save(server)
        return mapOf("id" to saved.id, "hostname" to saved.hostname, "status" to saved.status.name)
    }

    fun adminRestartServer(serverId: String): Map<String, Any?> {
        val server =
                serverRepo.findByIdOrNull(serverId) ?: throw NotFoundException("Server", serverId)
        contabo.restartInstance(server.contaboInstanceId.toLong())
        return mapOf(
                "id" to server.id,
                "hostname" to server.hostname,
                "message" to "Server restarting"
        )
    }

    fun adminSuspendServer(serverId: String): Map<String, Any?> {
        val server =
                serverRepo.findByIdOrNull(serverId) ?: throw NotFoundException("Server", serverId)
        val user = userRepo.findByIdOrNull(server.userId)
        contabo.stopInstance(server.contaboInstanceId.toLong())
        server.status = ServerStatus.SUSPENDED
        val saved = serverRepo.save(server)
        if (user != null) {
            val deleteDate = java.time.Instant.now().plusSeconds(14 * 86400L).toString()
            try {
                notifications.sendServiceSuspendedEmail(
                        user.email,
                        server.hostname,
                        deleteDate,
                        user.language,
                        user.id
                )
            } catch (e: Exception) {
                logger.error("Failed service-suspended email: ${e.message}")
            }
        }
        return mapOf("id" to saved.id, "hostname" to saved.hostname, "status" to saved.status.name)
    }

    fun adminUnsuspendServer(serverId: String): Map<String, Any?> {
        val server =
                serverRepo.findByIdOrNull(serverId) ?: throw NotFoundException("Server", serverId)
        if (server.status != ServerStatus.SUSPENDED)
                throw BadRequestException("Server is not suspended")
        contabo.startInstance(server.contaboInstanceId.toLong())
        server.status = ServerStatus.RUNNING
        val saved = serverRepo.save(server)
        return mapOf("id" to saved.id, "hostname" to saved.hostname, "status" to saved.status.name)
    }

    fun adminForceServerStatus(serverId: String, status: String): Map<String, Any?> {
        if (status !in listOf("RUNNING", "STOPPED", "ERROR"))
                throw BadRequestException("Status must be RUNNING, STOPPED, or ERROR")
        val server =
                serverRepo.findByIdOrNull(serverId) ?: throw NotFoundException("Server", serverId)
        server.status = ServerStatus.valueOf(status)
        val saved = serverRepo.save(server)
        return mapOf("id" to saved.id, "hostname" to saved.hostname, "status" to saved.status.name)
    }

    fun updateServerStatus(serverId: String, dto: UpdateServerStatusDto): Map<String, Any?> {
        val server =
                serverRepo.findByIdOrNull(serverId) ?: throw NotFoundException("Server", serverId)
        server.status = ServerStatus.valueOf(dto.status)
        val saved = serverRepo.save(server)
        return mapOf("id" to saved.id, "hostname" to saved.hostname, "status" to saved.status.name)
    }

    fun getServerLogs(serverId: String) =
            provisioningLogRepo.findByServerIdOrderByCreatedAtAsc(serverId)

    fun adminReprovisionServer(serverId: String): Map<String, String> {
        val server =
                serverRepo.findByIdOrNull(serverId) ?: throw NotFoundException("Server", serverId)
        if (server.ipAddress == null) throw BadRequestException("Server has no IP address yet")
        if (server.rootPassword.isBlank())
                throw BadRequestException("Server has no stored credentials")

        server.status = ServerStatus.NEEDS_PROVISION
        serverRepo.save(server)
        logger.info("Admin reprovision queued for $serverId")
        return mapOf("message" to "Post-provisioning started", "serverId" to serverId)
    }

    // ════════════════════════════════════════════════════════════
    // Orders
    // ════════════════════════════════════════════════════════════

    fun listOrders(filter: AdminOrdersFilterDto): PaginatedResponse<Map<String, Any?>> {
        var orders = orderRepo.findAll()
        if (filter.status != null) orders = orders.filter { it.status.name == filter.status }
        if (filter.userId != null) orders = orders.filter { it.userId == filter.userId }
        val total = orders.size.toLong()
        val sorted = orders.sortedByDescending { it.createdAt }
        val pageable = PageRequest.of((filter.page ?: 1) - 1, filter.limit ?: 20)
        val begin = pageable.offset.toInt()
        val end = minOf(begin + (filter.limit ?: 20), sorted.size)
        val paged = if (begin < sorted.size) sorted.subList(begin, end) else emptyList()
        val data =
                paged.map { o ->
                    val user = userRepo.findByIdOrNull(o.userId)
                    val plan = o.planId?.let { planRepo.findByIdOrNull(it) }
                    mapOf(
                            "id" to o.id,
                            "status" to o.status.name,
                            "totalAmount" to o.totalAmount,
                            "billingCycle" to o.billingCycle,
                            "region" to o.region,
                            "paymentMethod" to o.paymentMethod.name,
                            "serviceType" to o.serviceType.name,
                            "stripePaymentId" to o.stripePaymentId,
                            "createdAt" to o.createdAt,
                            "user" to
                                    user?.let {
                                        mapOf(
                                                "id" to it.id,
                                                "email" to it.email,
                                                "firstName" to it.firstName,
                                                "lastName" to it.lastName
                                        )
                                    },
                            "plan" to plan?.let { mapOf("name" to it.name, "slug" to it.slug) },
                    )
                }
        return PaginatedResponse(
                data,
                total,
                filter.page ?: 1,
                filter.limit ?: 20,
                ((total + (filter.limit ?: 20) - 1) / (filter.limit ?: 20)).toInt()
        )
    }

    fun getOrderDetail(orderId: String): Map<String, Any?> {
        val order = orderRepo.findByIdOrNull(orderId) ?: throw NotFoundException("Order", orderId)
        val user = userRepo.findByIdOrNull(order.userId)
        val plan = order.planId?.let { planRepo.findByIdOrNull(it) }
        val server = serverRepo.findByOrderId(orderId)
        val invoices = invoiceRepo.findByOrderId(orderId)
        return mapOf(
                "id" to order.id,
                "status" to order.status.name,
                "totalAmount" to order.totalAmount,
                "billingCycle" to order.billingCycle,
                "region" to order.region,
                "os" to order.os,
                "paymentMethod" to order.paymentMethod.name,
                "stripePaymentId" to order.stripePaymentId,
                "createdAt" to order.createdAt,
                "updatedAt" to order.updatedAt,
                "user" to
                        user?.let {
                            mapOf(
                                    "id" to it.id,
                                    "email" to it.email,
                                    "firstName" to it.firstName,
                                    "lastName" to it.lastName
                            )
                        },
                "plan" to plan,
                "server" to
                        server?.let {
                            mapOf(
                                    "id" to it.id,
                                    "hostname" to it.hostname,
                                    "ipAddress" to it.ipAddress,
                                    "status" to it.status.name,
                                    "os" to it.os,
                                    "region" to it.region,
                                    "contaboInstanceId" to it.contaboInstanceId,
                                    "provisionedAt" to it.provisionedAt,
                                    "expiresAt" to it.expiresAt
                            )
                        },
                "invoices" to
                        invoices.map {
                            mapOf(
                                    "id" to it.id,
                                    "amount" to it.amount,
                                    "currency" to it.currency,
                                    "stripeInvoiceId" to it.stripeInvoiceId,
                                    "paidAt" to it.paidAt,
                                    "createdAt" to it.createdAt
                            )
                        },
        )
    }

    fun updateOrderStatus(orderId: String, dto: UpdateOrderStatusDto): Map<String, Any?> {
        val order = orderRepo.findByIdOrNull(orderId) ?: throw NotFoundException("Order", orderId)
        order.status = OrderStatus.valueOf(dto.status)
        val saved = orderRepo.save(order)
        return mapOf(
                "id" to saved.id,
                "status" to saved.status.name,
                "updatedAt" to saved.updatedAt
        )
    }

    // ════════════════════════════════════════════════════════════
    // Invoices
    // ════════════════════════════════════════════════════════════

    fun listInvoices(filter: AdminInvoicesFilterDto): PaginatedResponse<Map<String, Any?>> {
        var invoices = invoiceRepo.findAll()
        if (filter.userId != null) invoices = invoices.filter { it.userId == filter.userId }
        if (filter.orderId != null) invoices = invoices.filter { it.orderId == filter.orderId }
        if (filter.paymentMethod != null)
                invoices = invoices.filter { it.paymentMethod.name == filter.paymentMethod }
        if (filter.dateFrom != null) {
            val from = Instant.parse(filter.dateFrom)
            invoices = invoices.filter { it.paidAt != null && it.paidAt!! >= from }
        }
        if (filter.dateTo != null) {
            val to = Instant.parse(filter.dateTo)
            invoices = invoices.filter { it.paidAt != null && it.paidAt!! <= to }
        }
        val total = invoices.size.toLong()
        val sorted = invoices.sortedByDescending { it.createdAt }
        val pageable = PageRequest.of((filter.page ?: 1) - 1, filter.limit ?: 20)
        val begin = pageable.offset.toInt()
        val end = minOf(begin + (filter.limit ?: 20), sorted.size)
        val paged = if (begin < sorted.size) sorted.subList(begin, end) else emptyList()
        val data =
                paged.map { i ->
                    val user = userRepo.findByIdOrNull(i.userId)
                    mapOf(
                            "id" to i.id,
                            "amount" to i.amount,
                            "currency" to i.currency,
                            "paymentMethod" to i.paymentMethod.name,
                            "stripeInvoiceId" to i.stripeInvoiceId,
                            "paidAt" to i.paidAt,
                            "createdAt" to i.createdAt,
                            "user" to
                                    user?.let {
                                        mapOf(
                                                "id" to it.id,
                                                "email" to it.email,
                                                "firstName" to it.firstName,
                                                "lastName" to it.lastName
                                        )
                                    },
                    )
                }
        return PaginatedResponse(
                data,
                total,
                filter.page ?: 1,
                filter.limit ?: 20,
                ((total + (filter.limit ?: 20) - 1) / (filter.limit ?: 20)).toInt()
        )
    }

    fun getInvoiceDetail(invoiceId: String): Map<String, Any?> {
        val invoice =
                invoiceRepo.findByIdOrNull(invoiceId)
                        ?: throw NotFoundException("Invoice", invoiceId)
        val user = userRepo.findByIdOrNull(invoice.userId)
        val order = orderRepo.findByIdOrNull(invoice.orderId)
        val plan = order?.planId?.let { planRepo.findByIdOrNull(it) }
        val server = order?.id?.let { serverRepo.findByOrderId(it) }
        return mapOf(
                "id" to invoice.id,
                "amount" to invoice.amount,
                "currency" to invoice.currency,
                "stripeInvoiceId" to invoice.stripeInvoiceId,
                "paidAt" to invoice.paidAt,
                "createdAt" to invoice.createdAt,
                "user" to
                        user?.let {
                            mapOf(
                                    "id" to it.id,
                                    "email" to it.email,
                                    "firstName" to it.firstName,
                                    "lastName" to it.lastName
                            )
                        },
                "order" to
                        order?.let {
                            mapOf(
                                    "id" to it.id,
                                    "status" to it.status.name,
                                    "billingCycle" to it.billingCycle,
                                    "plan" to plan
                            )
                        },
                "server" to
                        server?.let {
                            mapOf(
                                    "id" to it.id,
                                    "hostname" to it.hostname,
                                    "ipAddress" to it.ipAddress,
                                    "status" to it.status.name
                            )
                        },
        )
    }

    // ════════════════════════════════════════════════════════════
    // Webhook Events
    // ════════════════════════════════════════════════════════════

    fun listWebhookEvents(): List<WebhookEvent> = webhookEventRepo.findAll()

    // ════════════════════════════════════════════════════════════
    // Sessions
    // ════════════════════════════════════════════════════════════

    fun getAllSessions(dto: PaginationDto): PaginatedResponse<Map<String, Any?>> {
        val sessions =
                refreshTokenRepo
                        .findAll()
                        .filter { it.revokedAt == null && it.expiresAt > Instant.now() }
                        .sortedByDescending { it.createdAt }
        val total = sessions.size.toLong()
        val pageable = PageRequest.of((dto.page - 1), dto.limit)
        val begin = pageable.offset.toInt()
        val end = minOf(begin + dto.limit, sessions.size)
        val paged = if (begin < sessions.size) sessions.subList(begin, end) else emptyList()
        val data =
                paged.map { s ->
                    val user = userRepo.findByIdOrNull(s.userId)
                    mapOf(
                            "id" to s.id,
                            "createdAt" to s.createdAt,
                            "expiresAt" to s.expiresAt,
                            "user" to
                                    user?.let {
                                        mapOf(
                                                "id" to it.id,
                                                "email" to it.email,
                                                "role" to it.role.name
                                        )
                                    }
                    )
                }
        return PaginatedResponse(
                data,
                total,
                dto.page,
                dto.limit,
                ((total + dto.limit - 1) / dto.limit).toInt()
        )
    }

    // ════════════════════════════════════════════════════════════
    // Plans
    // ════════════════════════════════════════════════════════════

    fun listPlans(filter: AdminPlansFilterDto): PaginatedResponse<Plan> {
        var plans = planRepo.findAll()
        if (filter.status != null) plans = plans.filter { it.status.name == filter.status }
        if (filter.productType != null)
                plans = plans.filter { it.productType.name == filter.productType }
        if (!filter.search.isNullOrBlank()) {
            val q = filter.search.lowercase()
            plans =
                    plans.filter {
                        it.name.lowercase().contains(q) ||
                                it.slug.lowercase().contains(q) ||
                                it.contaboPlanId.lowercase().contains(q)
                    }
        }
        val total = plans.size.toLong()
        val sorted = plans.sortedBy { it.sortOrder }
        val pageable = PageRequest.of((filter.page ?: 1) - 1, filter.limit ?: 20)
        val begin = pageable.offset.toInt()
        val end = minOf(begin + (filter.limit ?: 20), sorted.size)
        val paged = if (begin < sorted.size) sorted.subList(begin, end) else emptyList()
        return PaginatedResponse(
                paged,
                total,
                filter.page ?: 1,
                filter.limit ?: 20,
                ((total + (filter.limit ?: 20) - 1) / (filter.limit ?: 20)).toInt()
        )
    }

    fun getPlanDetail(planId: String): Plan =
            planRepo.findByIdOrNull(planId) ?: throw NotFoundException("Plan", planId)

    fun createPlan(dto: CreatePlanDto): Plan =
            planRepo.save(
                    Plan(
                            name = dto.name,
                            slug = dto.slug,
                            productType = ProductType.valueOf(dto.productType),
                            description = dto.description,
                            cpu = dto.cpu,
                            ram = dto.ram,
                            disk = dto.disk,
                            diskType = dto.diskType ?: "NVMe",
                            bandwidth = dto.bandwidth,
                            portSpeed = dto.portSpeed,
                            snapshots = dto.snapshots ?: 0,
                            price1Month = dto.price1Month,
                            price6Months = dto.price6Months,
                            price12Months = dto.price12Months,
                            setup1Month = dto.setup1Month,
                            setup6Months = dto.setup6Months,
                            setup12Months = dto.setup12Months,
                            priceMonthly = dto.price1Month,
                            contaboPlanId = dto.contaboPlanId,
                            contaboCostPrice = dto.contaboCostPrice,
                            regions = mapper.valueToTree(dto.regions ?: emptyList<String>()),
                            availableAddons = mapper.valueToTree(dto.availableAddons ?: emptyList<String>()),
                            storageTB = dto.storageTB,
                            sortOrder = dto.sortOrder ?: 0,
                    )
            )

    fun updatePlan(planId: String, dto: UpdatePlanDto): Plan {
        val plan = planRepo.findByIdOrNull(planId) ?: throw NotFoundException("Plan", planId)
        dto.name?.let { plan.name = it }
        dto.description?.let { plan.description = it }
        dto.cpu?.let { plan.cpu = it }
        dto.ram?.let { plan.ram = it }
        dto.disk?.let { plan.disk = it }
        dto.price1Month?.let {
            plan.price1Month = it
            plan.priceMonthly = it
        }
        dto.price6Months?.let { plan.price6Months = it }
        dto.price12Months?.let { plan.price12Months = it }
        dto.status?.let { plan.status = PlanStatus.valueOf(it) }
        dto.sortOrder?.let { plan.sortOrder = it }
        dto.slug?.let { plan.slug = it }
        dto.productType?.let { plan.productType = ProductType.valueOf(it) }
        dto.diskType?.let { plan.diskType = it }
        dto.bandwidth?.let { plan.bandwidth = it }
        dto.portSpeed?.let { plan.portSpeed = it }
        dto.snapshots?.let { plan.snapshots = it }
        dto.priceMonthly?.let { plan.priceMonthly = it }
        dto.setup1Month?.let { plan.setup1Month = it }
        dto.setup6Months?.let { plan.setup6Months = it }
        dto.setup12Months?.let { plan.setup12Months = it }
        dto.contaboPlanId?.let { plan.contaboPlanId = it }
        dto.contaboCostPrice?.let { plan.contaboCostPrice = it }
        dto.regions?.let { plan.regions = mapper.valueToTree(it) }
        dto.availableAddons?.let { plan.availableAddons = mapper.valueToTree(it) }
        dto.storageTB?.let { plan.storageTB = it }
        return planRepo.save(plan)
    }

    fun deletePlan(planId: String): Any {
        val plan = planRepo.findByIdOrNull(planId) ?: throw NotFoundException("Plan", planId)
        val orderCount = orderRepo.findAll().count { it.planId == planId }
        if (orderCount > 0) {
            plan.status = PlanStatus.ARCHIVED
            return planRepo.save(plan)
        }
        planRepo.delete(plan)
        return mapOf("message" to "Plan deleted")
    }

    fun updatePlanStatus(planId: String, dto: UpdatePlanStatusDto): Plan {
        val plan = planRepo.findByIdOrNull(planId) ?: throw NotFoundException("Plan", planId)
        plan.status = PlanStatus.valueOf(dto.status)
        return planRepo.save(plan)
    }

    fun updatePlanAddons(planId: String, dto: UpdatePlanAddonsDto): Map<String, Any?> {
        val plan = planRepo.findByIdOrNull(planId) ?: throw NotFoundException("Plan", planId)
        plan.availableAddons = mapper.valueToTree(dto.addons)
        planRepo.save(plan)
        return mapOf("planId" to planId, "addons" to dto.addons)
    }

    fun updatePlanAddon(
        planId: String,
        addonId: String,
        dto: UpdateSingleAddonPriceDto,
    ): Map<String, Any?> {
        val plan = planRepo.findByIdOrNull(planId) ?: throw NotFoundException("Plan", planId)
        val addons = (plan.availableAddons as? com.fasterxml.jackson.databind.node.ArrayNode)
            ?.map { mapper.treeToValue(it, PlanAddon::class.java) }
            ?.toMutableList()
            ?: throw BadRequestException("Plan has no addons configured")
        val idx = addons.indexOfFirst { it.id == addonId }
        if (idx == -1) throw NotFoundException("Addon", addonId)
        val current = addons[idx]
        addons[idx] = current.copy(
            priceMonthly = dto.priceMonthly ?: current.priceMonthly,
            regionPrices = dto.regionPrices ?: current.regionPrices,
        )
        plan.availableAddons = mapper.valueToTree(addons)
        planRepo.save(plan)
        return mapOf("planId" to planId, "addonId" to addonId, "updated" to addons[idx])
    }

    // ════════════════════════════════════════════════════════════
    // Addon Catalog
    // ════════════════════════════════════════════════════════════

    fun listAddonCatalog() = addonCatalogRepo.findAllByOrderBySortOrderAsc()

    fun createAddonCatalog(dto: CreateAddonCatalogDto): AddonCatalog {
        if (addonCatalogRepo.existsById(dto.id))
            throw BadRequestException("Addon '${dto.id}' already exists")
        return addonCatalogRepo.save(
            AddonCatalog(
                id = dto.id,
                category = dto.category,
                label = dto.label,
                contaboValue = dto.contaboValue,
                billingType = dto.billingType,
                isDefault = dto.isDefault,
                sortOrder = dto.sortOrder,
            )
        )
    }

    fun updateAddonCatalog(id: String, dto: UpdateAddonCatalogDto): AddonCatalog {
        val addon = addonCatalogRepo.findByIdOrNull(id) ?: throw NotFoundException("Addon", id)
        dto.category?.let { addon.category = it }
        dto.label?.let { addon.label = it }
        dto.contaboValue?.let { addon.contaboValue = it }
        dto.billingType?.let { addon.billingType = it }
        dto.isDefault?.let { addon.isDefault = it }
        dto.sortOrder?.let { addon.sortOrder = it }
        return addonCatalogRepo.save(addon)
    }

    fun deleteAddonCatalog(id: String): Map<String, String> {
        if (!addonCatalogRepo.existsById(id)) throw NotFoundException("Addon", id)
        addonCatalogRepo.deleteById(id)
        return mapOf("message" to "Addon deleted")
    }

    // ════════════════════════════════════════════════════════════
    // TLD Pricing
    // ════════════════════════════════════════════════════════════

    fun listTldPricing(): List<Map<String, Any?>> =
            domainTldPricingRepo.findAll().sortedBy { it.tld }.map {
                mapOf(
                        "id" to it.id,
                        "tld" to it.tld,
                        "registrationPrice" to it.registrationPrice,
                        "renewalPrice" to it.renewalPrice,
                        "transferPrice" to it.transferPrice,
                        "available" to it.available,
                        "createdAt" to it.createdAt,
                        "updatedAt" to it.updatedAt
                )
            }

    fun createTldPricing(dto: CreateTldPricingDto): DomainTldPricing {
        val existing = domainTldPricingRepo.findByTld(dto.tld)
        if (existing != null) throw BadRequestException("TLD ${dto.tld} already exists")
        return domainTldPricingRepo.save(
                DomainTldPricing(
                        tld = dto.tld,
                        registrationPrice = dto.registrationPrice,
                        renewalPrice = dto.renewalPrice,
                        transferPrice = dto.transferPrice,
                        available = dto.available,
                )
        )
    }

    fun updateTldPricing(id: String, dto: UpdateTldPricingDto): DomainTldPricing {
        val tld =
                domainTldPricingRepo.findByIdOrNull(id) ?: throw NotFoundException("TldPricing", id)
        dto.registrationPrice?.let { tld.registrationPrice = it }
        dto.renewalPrice?.let { tld.renewalPrice = it }
        dto.transferPrice?.let { tld.transferPrice = it }
        dto.available?.let { tld.available = it }
        return domainTldPricingRepo.save(tld)
    }

    fun deleteTldPricing(id: String): Map<String, String> {
        val tld =
                domainTldPricingRepo.findByIdOrNull(id) ?: throw NotFoundException("TldPricing", id)
        val inUse = domainRepo.findAll().count { it.tldPricingId == id }
        if (inUse > 0)
                throw BadRequestException("TLD is in use by existing domains and cannot be deleted")
        domainTldPricingRepo.delete(tld)
        return mapOf("message" to "TLD pricing deleted")
    }

    fun seedTldPricing(): Map<String, Any> {
        val default =
                listOf(
                        Triple(".com", 12.99, 14.99),
                        Triple(".net", 13.99, 15.99),
                        Triple(".org", 13.99, 15.99),
                        Triple(".info", 14.99, 17.99),
                        Triple(".biz", 14.99, 17.99),
                        Triple(".io", 39.99, 49.99),
                        Triple(".dev", 14.99, 17.99),
                        Triple(".app", 14.99, 17.99),
                        Triple(".cloud", 9.99, 12.99),
                        Triple(".online", 7.99, 9.99),
                        Triple(".site", 7.99, 9.99),
                        Triple(".tech", 12.99, 15.99),
                        Triple(".store", 12.99, 59.99),
                        Triple(".xyz", 2.99, 12.99),
                        Triple(".us", 8.99, 10.99),
                        Triple(".co", 19.99, 29.99),
                        Triple(".uk", 7.99, 9.99),
                        Triple(".de", 6.99, 8.99),
                        Triple(".eu", 7.99, 9.99),
                        Triple(".es", 9.99, 11.99),
                        Triple(".fr", 9.99, 11.99),
                        Triple(".it", 9.99, 11.99),
                        Triple(".nl", 9.99, 11.99),
                        Triple(".ca", 14.99, 16.99),
                        Triple(".au", 12.99, 14.99),
                        Triple(".pro", 14.99, 17.99),
                        Triple(".blog", 24.99, 29.99),
                        Triple(".news", 19.99, 24.99),
                        Triple(".shop", 34.99, 39.99),
                )
        var created = 0
        var skipped = 0
        for ((tld, registration, renewal) in default) {
            val existing = domainTldPricingRepo.findByTld(tld)
            if (existing == null) {
                domainTldPricingRepo.save(
                        DomainTldPricing(
                                tld = tld,
                                registrationPrice = BigDecimal(registration),
                                renewalPrice = BigDecimal(renewal),
                                transferPrice = BigDecimal(registration),
                                available = true,
                        )
                )
                created++
            } else skipped++
        }
        return mapOf(
                "message" to "Seed complete",
                "created" to created,
                "skipped" to skipped,
                "total" to default.size
        )
    }

    // ════════════════════════════════════════════════════════════
    // Domains (admin view)
    // ════════════════════════════════════════════════════════════

    fun getDomains(dto: AdminDomainsFilterDto): Map<String, Any> {
        var domains = domainRepo.findAll()
        if (dto.userId != null) domains = domains.filter { it.userId == dto.userId }
        if (dto.status != null) domains = domains.filter { it.status.name == dto.status }
        val total = domains.size.toLong()
        val sorted = domains.sortedByDescending { it.createdAt }
        val pageable = PageRequest.of((dto.page - 1), dto.limit)
        val begin = pageable.offset.toInt()
        val end = minOf(begin + dto.limit, sorted.size)
        val paged = if (begin < sorted.size) sorted.subList(begin, end) else emptyList()
        val data =
                paged.map { d ->
                    val user = userRepo.findByIdOrNull(d.userId)
                    val tld = domainTldPricingRepo.findByIdOrNull(d.tldPricingId)
                    mapOf(
                            "id" to d.id,
                            "domainName" to d.domainName,
                            "status" to d.status.name,
                            "autoRenew" to d.autoRenew,
                            "whoisPrivacy" to d.whoisPrivacy,
                            "registeredAt" to d.registeredAt,
                            "expiresAt" to d.expiresAt,
                            "createdAt" to d.createdAt,
                            "user" to
                                    user?.let {
                                        mapOf(
                                                "id" to it.id,
                                                "email" to it.email,
                                                "firstName" to it.firstName,
                                                "lastName" to it.lastName
                                        )
                                    },
                            "tldPricing" to
                                    tld?.let {
                                        mapOf("tld" to it.tld, "renewalPrice" to it.renewalPrice)
                                    },
                    )
                }
        return mapOf(
                "data" to data,
                "total" to total,
                "page" to dto.page,
                "limit" to dto.limit,
                "totalPages" to ((total + dto.limit - 1) / dto.limit).toInt()
        )
    }

    fun getDomainDetail(id: String): Map<String, Any?> {
        val domain = domainRepo.findByIdOrNull(id) ?: throw NotFoundException("Domain", id)
        val user = userRepo.findByIdOrNull(domain.userId)
        val tld = domainTldPricingRepo.findByIdOrNull(domain.tldPricingId)
        val ownerHandle = domainHandleRepo.findByIdOrNull(domain.ownerHandleId)
        val adminHandle = domainHandleRepo.findByIdOrNull(domain.adminHandleId)
        val techHandle = domainHandleRepo.findByIdOrNull(domain.techHandleId)
        val zoneHandle = domainHandleRepo.findByIdOrNull(domain.zoneHandleId)
        return mapOf(
                "id" to domain.id,
                "domainName" to domain.domainName,
                "status" to domain.status.name,
                "authCode" to domain.authCode,
                "autoRenew" to domain.autoRenew,
                "whoisPrivacy" to domain.whoisPrivacy,
                "nameservers" to domain.nameservers,
                "registeredAt" to domain.registeredAt,
                "expiresAt" to domain.expiresAt,
                "createdAt" to domain.createdAt,
                "updatedAt" to domain.updatedAt,
                "user" to
                        user?.let {
                            mapOf(
                                    "id" to it.id,
                                    "email" to it.email,
                                    "firstName" to it.firstName,
                                    "lastName" to it.lastName
                            )
                        },
                "tldPricing" to tld,
                "ownerHandle" to ownerHandle,
                "adminHandle" to adminHandle,
                "techHandle" to techHandle,
                "zoneHandle" to zoneHandle,
        )
    }

    fun updateDomainStatus(id: String, dto: UpdateDomainStatusDto): Domain {
        val domain = domainRepo.findByIdOrNull(id) ?: throw NotFoundException("Domain", id)
        val previousStatus = domain.status
        domain.status = DomainStatus.valueOf(dto.status)
        val saved = domainRepo.save(domain)
        if (dto.status == "ACTIVE" && previousStatus != DomainStatus.ACTIVE) {
            val user = userRepo.findByIdOrNull(domain.userId)
            if (user != null) {
                try {
                    notifications.sendDomainRegisteredEmail(
                            user.email,
                            domain.domainName,
                            domain.expiresAt.toString(),
                            user.language,
                            user.id
                    )
                } catch (e: Exception) {
                    logger.warn("Failed domain registered email: ${e.message}")
                }
            }
        }
        return saved
    }

    // ════════════════════════════════════════════════════════════
    // Object Storage (admin view)
    // ════════════════════════════════════════════════════════════

    fun getObjectStorages(dto: AdminObjectStoragesFilterDto): Map<String, Any> {
        var storages = objectStorageRepo.findAll()
        if (dto.userId != null) storages = storages.filter { it.userId == dto.userId }
        if (dto.status != null) storages = storages.filter { it.status.name == dto.status }
        val total = storages.size.toLong()
        val sorted = storages.sortedByDescending { it.createdAt }
        val pageable = PageRequest.of((dto.page - 1), dto.limit)
        val begin = pageable.offset.toInt()
        val end = minOf(begin + dto.limit, sorted.size)
        val paged = if (begin < sorted.size) sorted.subList(begin, end) else emptyList()
        val data =
                paged.map { s ->
                    val user = userRepo.findByIdOrNull(s.userId)
                    mapOf(
                            "id" to s.id,
                            "displayName" to s.displayName,
                            "status" to s.status.name,
                            "region" to s.region,
                            "totalPurchasedSpaceTB" to s.totalPurchasedSpaceTB,
                            "usedSpaceTB" to s.usedSpaceTB,
                            "s3Endpoint" to s.s3Endpoint,
                            "provisionedAt" to s.provisionedAt,
                            "expiresAt" to s.expiresAt,
                            "createdAt" to s.createdAt,
                            "user" to
                                    user?.let {
                                        mapOf(
                                                "id" to it.id,
                                                "email" to it.email,
                                                "firstName" to it.firstName,
                                                "lastName" to it.lastName
                                        )
                                    },
                    )
                }
        return mapOf(
                "data" to data,
                "total" to total,
                "page" to dto.page,
                "limit" to dto.limit,
                "totalPages" to ((total + dto.limit - 1) / dto.limit).toInt()
        )
    }

    fun getObjectStorageDetail(id: String): Map<String, Any?> {
        val storage =
                objectStorageRepo.findByIdOrNull(id) ?: throw NotFoundException("ObjectStorage", id)
        val user = userRepo.findByIdOrNull(storage.userId)
        return mapOf(
                "id" to storage.id,
                "displayName" to storage.displayName,
                "status" to storage.status.name,
                "region" to storage.region,
                "totalPurchasedSpaceTB" to storage.totalPurchasedSpaceTB,
                "usedSpaceTB" to storage.usedSpaceTB,
                "autoScaling" to storage.autoScaling,
                "s3Endpoint" to storage.s3Endpoint,
                "provisionedAt" to storage.provisionedAt,
                "expiresAt" to storage.expiresAt,
                "createdAt" to storage.createdAt,
                "updatedAt" to storage.updatedAt,
                "user" to
                        user?.let {
                            mapOf(
                                    "id" to it.id,
                                    "email" to it.email,
                                    "firstName" to it.firstName,
                                    "lastName" to it.lastName
                            )
                        },
        )
    }

    fun updateObjectStorageStatus(id: String, dto: UpdateObjectStorageStatusDto): ObjectStorage {
        val storage =
                objectStorageRepo.findByIdOrNull(id) ?: throw NotFoundException("ObjectStorage", id)
        storage.status = ObjectStorageStatus.valueOf(dto.status)
        return objectStorageRepo.save(storage)
    }

    // ════════════════════════════════════════════════════════════
    // Firewalls (admin view)
    // ════════════════════════════════════════════════════════════

    fun getFirewalls(dto: AdminFirewallsFilterDto): Map<String, Any> {
        var fws = firewallRepo.findAll()
        if (dto.userId != null) fws = fws.filter { it.userId == dto.userId }
        val total = fws.size.toLong()
        val sorted = fws.sortedByDescending { it.createdAt }
        val pageable = PageRequest.of((dto.page - 1), dto.limit)
        val begin = pageable.offset.toInt()
        val end = minOf(begin + dto.limit, sorted.size)
        val paged = if (begin < sorted.size) sorted.subList(begin, end) else emptyList()
        val data =
                paged.map { fw ->
                    val user = userRepo.findByIdOrNull(fw.userId)
                    val rules = firewallRuleRepo.findByFirewallId(fw.id)
                    val assignments = firewallAssignmentRepo.findByFirewallId(fw.id)
                    mapOf(
                            "id" to fw.id,
                            "name" to fw.name,
                            "description" to fw.description,
                            "status" to fw.status,
                            "contaboFirewallId" to fw.contaboFirewallId,
                            "createdAt" to fw.createdAt,
                            "user" to
                                    user?.let {
                                        mapOf(
                                                "id" to it.id,
                                                "email" to it.email,
                                                "firstName" to it.firstName,
                                                "lastName" to it.lastName
                                        )
                                    },
                            "rulesCount" to rules.size,
                            "assignmentsCount" to assignments.size,
                    )
                }
        return mapOf(
                "data" to data,
                "total" to total,
                "page" to dto.page,
                "limit" to dto.limit,
                "totalPages" to ((total + dto.limit - 1) / dto.limit).toInt()
        )
    }

    fun getFirewallDetail(id: String): Map<String, Any?> {
        val fw = firewallRepo.findByIdOrNull(id) ?: throw NotFoundException("Firewall", id)
        val user = userRepo.findByIdOrNull(fw.userId)
        val rules = firewallRuleRepo.findByFirewallId(id)
        val assignments = firewallAssignmentRepo.findByFirewallId(id)
        val assignedServers =
                assignments.mapNotNull { a ->
                    serverRepo.findByIdOrNull(a.serverId)?.let {
                        mapOf("id" to it.id, "hostname" to it.hostname, "ipAddress" to it.ipAddress)
                    }
                }
        return mapOf(
                "id" to fw.id,
                "name" to fw.name,
                "description" to fw.description,
                "status" to fw.status,
                "contaboFirewallId" to fw.contaboFirewallId,
                "createdAt" to fw.createdAt,
                "updatedAt" to fw.updatedAt,
                "user" to
                        user?.let {
                            mapOf(
                                    "id" to it.id,
                                    "email" to it.email,
                                    "firstName" to it.firstName,
                                    "lastName" to it.lastName
                            )
                        },
                "rules" to rules,
                "assignments" to assignedServers,
        )
    }

    // ════════════════════════════════════════════════════════════
    // Snapshots (admin view)
    // ════════════════════════════════════════════════════════════

    fun getSnapshots(dto: AdminSnapshotsFilterDto): Map<String, Any> {
        var snaps = snapshotRepo.findAll()
        if (dto.serverId != null) snaps = snaps.filter { it.serverId == dto.serverId }
        val total = snaps.size.toLong()
        val sorted = snaps.sortedByDescending { it.createdAt }
        val pageable = PageRequest.of((dto.page - 1), dto.limit)
        val begin = pageable.offset.toInt()
        val end = minOf(begin + dto.limit, sorted.size)
        val paged = if (begin < sorted.size) sorted.subList(begin, end) else emptyList()
        val data =
                paged.map { s ->
                    val server = serverRepo.findByIdOrNull(s.serverId)
                    val user = server?.let { userRepo.findByIdOrNull(it.userId) }
                    mapOf(
                            "id" to s.id,
                            "name" to s.name,
                            "description" to s.description,
                            "contaboSnapshotId" to s.contaboSnapshotId,
                            "createdAt" to s.createdAt,
                            "server" to
                                    server?.let {
                                        mapOf(
                                                "id" to it.id,
                                                "hostname" to it.hostname,
                                                "ipAddress" to it.ipAddress
                                        )
                                    },
                            "user" to user?.let { mapOf("id" to it.id, "email" to it.email) },
                    )
                }
        return mapOf(
                "data" to data,
                "total" to total,
                "page" to dto.page,
                "limit" to dto.limit,
                "totalPages" to ((total + dto.limit - 1) / dto.limit).toInt()
        )
    }

    fun getSnapshotDetail(id: String): Map<String, Any?> {
        val snap = snapshotRepo.findByIdOrNull(id) ?: throw NotFoundException("Snapshot", id)
        val server = serverRepo.findByIdOrNull(snap.serverId)
        val user = server?.let { userRepo.findByIdOrNull(it.userId) }
        return mapOf(
                "id" to snap.id,
                "name" to snap.name,
                "description" to snap.description,
                "contaboSnapshotId" to snap.contaboSnapshotId,
                "createdAt" to snap.createdAt,
                "server" to
                        server?.let {
                            mapOf(
                                    "id" to it.id,
                                    "hostname" to it.hostname,
                                    "ipAddress" to it.ipAddress,
                                    "region" to it.region,
                                    "contaboInstanceId" to it.contaboInstanceId
                            )
                        },
                "user" to
                        user?.let {
                            mapOf(
                                    "id" to it.id,
                                    "email" to it.email,
                                    "firstName" to it.firstName,
                                    "lastName" to it.lastName
                            )
                        },
        )
    }

    // ════════════════════════════════════════════════════════════
    // Private Networks (admin view)
    // ════════════════════════════════════════════════════════════

    fun getPrivateNetworks(dto: AdminPrivateNetworksFilterDto): Map<String, Any> {
        var nets = privateNetworkRepo.findAll()
        if (dto.userId != null) nets = nets.filter { it.userId == dto.userId }
        val total = nets.size.toLong()
        val sorted = nets.sortedByDescending { it.createdAt }
        val pageable = PageRequest.of((dto.page - 1), dto.limit)
        val begin = pageable.offset.toInt()
        val end = minOf(begin + dto.limit, sorted.size)
        val paged = if (begin < sorted.size) sorted.subList(begin, end) else emptyList()
        val data =
                paged.map { n ->
                    val user = userRepo.findByIdOrNull(n.userId)
                    val assignments = privateNetworkAssignmentRepo.findByPrivateNetworkId(n.id)
                    mapOf(
                            "id" to n.id,
                            "name" to n.name,
                            "description" to n.description,
                            "region" to n.region,
                            "dataCenter" to n.dataCenter,
                            "cidr" to n.cidr,
                            "contaboNetworkId" to n.contaboNetworkId,
                            "createdAt" to n.createdAt,
                            "user" to
                                    user?.let {
                                        mapOf(
                                                "id" to it.id,
                                                "email" to it.email,
                                                "firstName" to it.firstName,
                                                "lastName" to it.lastName
                                        )
                                    },
                            "assignmentsCount" to assignments.size,
                    )
                }
        return mapOf(
                "data" to data,
                "total" to total,
                "page" to dto.page,
                "limit" to dto.limit,
                "totalPages" to ((total + dto.limit - 1) / dto.limit).toInt()
        )
    }

    fun getPrivateNetworkDetail(id: String): Map<String, Any?> {
        val net =
                privateNetworkRepo.findByIdOrNull(id)
                        ?: throw NotFoundException("PrivateNetwork", id)
        val user = userRepo.findByIdOrNull(net.userId)
        val assignments = privateNetworkAssignmentRepo.findByPrivateNetworkId(id)
        val assignedServers =
                assignments.mapNotNull { a ->
                    serverRepo.findByIdOrNull(a.serverId)?.let {
                        mapOf("id" to it.id, "hostname" to it.hostname, "ipAddress" to it.ipAddress)
                    }
                }
        return mapOf(
                "id" to net.id,
                "name" to net.name,
                "description" to net.description,
                "region" to net.region,
                "dataCenter" to net.dataCenter,
                "cidr" to net.cidr,
                "contaboNetworkId" to net.contaboNetworkId,
                "createdAt" to net.createdAt,
                "updatedAt" to net.updatedAt,
                "user" to
                        user?.let {
                            mapOf(
                                    "id" to it.id,
                                    "email" to it.email,
                                    "firstName" to it.firstName,
                                    "lastName" to it.lastName
                            )
                        },
                "assignments" to assignedServers,
        )
    }

    // ════════════════════════════════════════════════════════════
    // VIPs (admin view)
    // ════════════════════════════════════════════════════════════

    fun getVips(dto: AdminVipsFilterDto): Map<String, Any> {
        var vips = vipRepo.findAll()
        if (dto.userId != null) vips = vips.filter { it.userId == dto.userId }
        val total = vips.size.toLong()
        val sorted = vips.sortedByDescending { it.createdAt }
        val pageable = PageRequest.of((dto.page - 1), dto.limit)
        val begin = pageable.offset.toInt()
        val end = minOf(begin + dto.limit, sorted.size)
        val paged = if (begin < sorted.size) sorted.subList(begin, end) else emptyList()
        val data =
                paged.map { v ->
                    val user = userRepo.findByIdOrNull(v.userId)
                    mapOf(
                            "id" to v.id,
                            "ip" to v.ip,
                            "resourceId" to v.resourceId,
                            "resourceType" to v.resourceType,
                            "ipVersion" to v.ipVersion,
                            "type" to v.type,
                            "dataCenter" to v.dataCenter,
                            "region" to v.region,
                            "createdAt" to v.createdAt,
                            "user" to
                                    user?.let {
                                        mapOf(
                                                "id" to it.id,
                                                "email" to it.email,
                                                "firstName" to it.firstName,
                                                "lastName" to it.lastName
                                        )
                                    },
                    )
                }
        return mapOf(
                "data" to data,
                "total" to total,
                "page" to dto.page,
                "limit" to dto.limit,
                "totalPages" to ((total + dto.limit - 1) / dto.limit).toInt()
        )
    }

    fun getVipDetail(id: String): Map<String, Any?> {
        val vip = vipRepo.findByIdOrNull(id) ?: throw NotFoundException("Vip", id)
        val user = userRepo.findByIdOrNull(vip.userId)
        return mapOf(
                "id" to vip.id,
                "ip" to vip.ip,
                "resourceId" to vip.resourceId,
                "resourceType" to vip.resourceType,
                "ipVersion" to vip.ipVersion,
                "type" to vip.type,
                "dataCenter" to vip.dataCenter,
                "region" to vip.region,
                "createdAt" to vip.createdAt,
                "updatedAt" to vip.updatedAt,
                "user" to
                        user?.let {
                            mapOf(
                                    "id" to it.id,
                                    "email" to it.email,
                                    "firstName" to it.firstName,
                                    "lastName" to it.lastName
                            )
                        },
        )
    }

    // ════════════════════════════════════════════════════════════
    // Admin Proxy: Contabo sync actions
    // ════════════════════════════════════════════════════════════

    fun adminSyncServer(serverId: String): Map<String, Any?> {
        val server =
                serverRepo.findByIdOrNull(serverId) ?: throw NotFoundException("Server", serverId)
        val instance = contabo.getInstance(server.contaboInstanceId.toLong())
        val newStatus =
                when (instance.status?.lowercase()) {
                    "running" -> ServerStatus.RUNNING
                    "stopped" -> ServerStatus.STOPPED
                    "error" -> ServerStatus.ERROR
                    else -> null
                }
        if (newStatus != null) server.status = newStatus
        server.contaboData = mapOf("status" to (instance.status ?: ""))
        val saved = serverRepo.save(server)
        return mapOf(
                "id" to saved.id,
                "hostname" to saved.hostname,
                "status" to saved.status.name,
                "contaboData" to saved.contaboData
        )
    }

    fun adminSyncDomain(domainId: String): Map<String, Any?> {
        val domain =
                domainRepo.findByIdOrNull(domainId) ?: throw NotFoundException("Domain", domainId)
        val contaboDomain = contabo.getDomain(domain.domainName)
        val newStatus =
                when (contaboDomain.status?.lowercase()) {
                    "active" -> DomainStatus.ACTIVE
                    "pending" -> DomainStatus.PENDING
                    "expired" -> DomainStatus.EXPIRED
                    "cancelled" -> DomainStatus.CANCELLED
                    "failed" -> throw BadRequestException("Domain sync failed")
                    else -> null
                }
        if (newStatus != null) domain.status = newStatus
        val saved = domainRepo.save(domain)
        return mapOf(
                "id" to saved.id,
                "domainName" to saved.domainName,
                "status" to saved.status.name,
                "nameservers" to saved.nameservers
        )
    }

    fun adminUpdateDomainNameservers(
            domainId: String,
            dto: UpdateNameserversDto
    ): Map<String, Any?> {
        val domain =
                domainRepo.findByIdOrNull(domainId) ?: throw NotFoundException("Domain", domainId)
        contabo.updateDomain(
                domain.domainName,
                com.valoracloud.api.contabo.ContaboUpdateDomainRequest(
                        nameservers =
                                dto.nameservers.map {
                                    com.valoracloud.api.contabo.ContaboNameserver(
                                            name = it.name,
                                            ip = it.ip
                                    )
                                }
                )
        )
        domain.nameservers = dto.nameservers.map { mapOf("name" to it.name, "ip" to (it.ip ?: "")) }
        val saved = domainRepo.save(domain)
        return mapOf(
                "id" to saved.id,
                "domainName" to saved.domainName,
                "nameservers" to saved.nameservers
        )
    }

    fun adminSyncObjectStorage(storageId: String): Map<String, Any?> {
        val storage =
                objectStorageRepo.findByIdOrNull(storageId)
                        ?: throw NotFoundException("ObjectStorage", storageId)
        val cs = contabo.getObjectStorage(storage.contaboStorageId)
        val newStatus =
                when (cs.status?.lowercase()) {
                    "ready" -> ObjectStorageStatus.READY
                    "provisioning" -> ObjectStorageStatus.PROVISIONING
                    "error" -> ObjectStorageStatus.ERROR
                    "cancelled" -> ObjectStorageStatus.CANCELLED
                    else -> null
                }
        if (newStatus != null) storage.status = newStatus
        cs.totalPurchasedSpaceTB?.let { storage.totalPurchasedSpaceTB = it }
        val saved = objectStorageRepo.save(storage)
        return mapOf(
                "id" to saved.id,
                "displayName" to saved.displayName,
                "status" to saved.status.name,
                "totalPurchasedSpaceTB" to saved.totalPurchasedSpaceTB
        )
    }

    // ════════════════════════════════════════════════════════════
    // Admin Proxy: Server actions (reinstall, password, snapshots)
    // ════════════════════════════════════════════════════════════

    fun adminReinstallServer(serverId: String, dto: ReinstallServerDto): Map<String, Any?> {
        val server =
                serverRepo.findByIdOrNull(serverId) ?: throw NotFoundException("Server", serverId)
        var resolvedImageId = dto.imageId
        val uuidRegex =
                Regex(
                        "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
                        RegexOption.IGNORE_CASE
                )
        if (!uuidRegex.matches(dto.imageId)) {
            val image =
                    contabo.findImageBySlug(dto.imageId)
                            ?: throw BadRequestException(
                                    "No image found for OS slug: ${dto.imageId}"
                            )
            resolvedImageId = image.imageId
        }
        val currentPassword = EncryptionUtil.decrypt(server.rootPassword, encryptionKey)
        var secretId: Long? = null
        try {
            val secret =
                    contabo.createSecret(
                            ContaboCreateSecretRequest(
                                    name = "reinstall-${serverId.take(8)}",
                                    type = ContaboSecretType.password,
                                    value = currentPassword
                            )
                    )
            secretId = secret.secretId
        } catch (e: Exception) {
            logger.warn("Could not create reinstall secret: ${e.message}")
        }

        contabo.reinstallInstance(server.contaboInstanceId.toLong(), resolvedImageId, secretId)
        server.status = ServerStatus.REINSTALLING
        serverRepo.save(server)

        // Poll until running (max 15 min)
        var ready = false
        for (i in 0 until 30) {
            Thread.sleep(30_000)
            try {
                val inst = contabo.getInstance(server.contaboInstanceId.toLong())
                if (inst.status == "running") {
                    ready = true
                    break
                }
            } catch (_: Exception) {
                /* poll again */
            }
        }

        secretId?.let {
            try {
                contabo.deleteSecret(it)
            } catch (e: Exception) {
                logger.warn("Could not delete secret: ${e.message}")
            }
        }
        server.status = if (ready) ServerStatus.RUNNING else ServerStatus.STOPPED
        server.os = resolvedImageId
        val saved = serverRepo.save(server)
        if (ready) {
            val user = userRepo.findByIdOrNull(server.userId)
            if (user != null) {
                try {
                    notifications.sendReinstallCompleteEmail(
                        email = user.email,
                        hostname = server.hostname,
                        newPassword = currentPassword,
                        language = user.language,
                        userId = user.id
                    )
                } catch (e: Exception) {
                    logger.error("Failed to send reinstall complete email: ${e.message}")
                }
            }
        }
        return mapOf(
                "id" to saved.id,
                "hostname" to saved.hostname,
                "status" to saved.status.name,
                "os" to saved.os
        )
    }

    fun adminGetServerLogs(serverId: String) =
            provisioningLogRepo.findByServerIdOrderByCreatedAtAsc(serverId)

    fun adminChangeServerPassword(
            serverId: String,
            dto: ChangeServerPasswordDto
    ): Map<String, String> {
        val server =
                serverRepo.findByIdOrNull(serverId) ?: throw NotFoundException("Server", serverId)
        if (server.ipAddress == null) throw BadRequestException("Server has no IP address yet")
        val encrypted = EncryptionUtil.encrypt(dto.password, encryptionKey)
        server.rootPassword = encrypted
        serverRepo.save(server)
        return mapOf("message" to "Password changed successfully")
    }

    // ─── Snapshot actions ───────────────────────────────

    fun adminCreateSnapshot(serverId: String, dto: CreateSnapshotDto): Snapshot {
        val server =
                serverRepo.findByIdOrNull(serverId) ?: throw NotFoundException("Server", serverId)
        val existingSnaps = snapshotRepo.findByServerId(serverId)
        if (existingSnaps.size >= 10)
                throw BadRequestException("Contabo limit: max 10 snapshots per instance")
        val cs =
                contabo.createSnapshot(
                        server.contaboInstanceId.toLong(),
                        ContaboCreateSnapshotRequest(name = dto.name, description = dto.description)
                )
        return snapshotRepo.save(
                Snapshot(
                        serverId = serverId,
                        contaboSnapshotId = cs.snapshotId,
                        name = cs.name,
                        description = cs.description
                )
        )
    }

    fun adminUpdateSnapshot(snapshotId: String, dto: UpdateSnapshotDto): Snapshot {
        val snap =
                snapshotRepo.findByIdOrNull(snapshotId)
                        ?: throw NotFoundException("Snapshot", snapshotId)
        val server =
                serverRepo.findByIdOrNull(snap.serverId)
                        ?: throw NotFoundException("Server", snap.serverId)
        val cs =
                contabo.updateSnapshot(
                        server.contaboInstanceId.toLong(),
                        snap.contaboSnapshotId,
                        ContaboUpdateSnapshotRequest(name = dto.name, description = dto.description)
                )
        dto.name?.let { snap.name = it }
        dto.description?.let { snap.description = it }
        return snapshotRepo.save(snap)
    }

    fun adminDeleteSnapshot(snapshotId: String): Map<String, String> {
        val snap =
                snapshotRepo.findByIdOrNull(snapshotId)
                        ?: throw NotFoundException("Snapshot", snapshotId)
        val server =
                serverRepo.findByIdOrNull(snap.serverId)
                        ?: throw NotFoundException("Server", snap.serverId)
        contabo.deleteSnapshot(server.contaboInstanceId.toLong(), snap.contaboSnapshotId)
        snapshotRepo.delete(snap)
        return mapOf("message" to "Snapshot deleted")
    }

    fun adminRollbackSnapshot(snapshotId: String): Map<String, Any?> {
        val snap =
                snapshotRepo.findByIdOrNull(snapshotId)
                        ?: throw NotFoundException("Snapshot", snapshotId)
        val server =
                serverRepo.findByIdOrNull(snap.serverId)
                        ?: throw NotFoundException("Server", snap.serverId)
        contabo.rollbackSnapshot(server.contaboInstanceId.toLong(), snap.contaboSnapshotId)
        return mapOf(
                "snapshotId" to snap.id,
                "serverId" to snap.serverId,
                "serverHostname" to server.hostname,
                "message" to "Snapshot rollback initiated"
        )
    }

    fun adminSyncSnapshots(serverId: String): List<Snapshot> {
        val server =
                serverRepo.findByIdOrNull(serverId) ?: throw NotFoundException("Server", serverId)
        val csSnaps = contabo.listSnapshots(server.contaboInstanceId.toLong())
        val contaboIds = csSnaps.map { it.snapshotId }.toSet()
        for (cs in csSnaps) {
            val existing = snapshotRepo.findByContaboSnapshotId(cs.snapshotId)
            if (existing != null) {
                existing.name = cs.name
                existing.description = cs.description
                snapshotRepo.save(existing)
            } else {
                snapshotRepo.save(
                        Snapshot(
                                serverId = serverId,
                                contaboSnapshotId = cs.snapshotId,
                                name = cs.name,
                                description = cs.description
                        )
                )
            }
        }
        // Remove stale
        val dbSnaps = snapshotRepo.findByServerId(serverId)
        dbSnaps.filter { !contaboIds.contains(it.contaboSnapshotId) }.forEach {
            snapshotRepo.delete(it)
        }
        return snapshotRepo.findByServerId(serverId)
    }

    // ════════════════════════════════════════════════════════════
    // Admin Proxy: Domain actions
    // ════════════════════════════════════════════════════════════

    fun adminCancelDomain(domainId: String, dto: CancelDomainDto): Domain {
        val domain =
                domainRepo.findByIdOrNull(domainId) ?: throw NotFoundException("Domain", domainId)
        val date = dto.cancelDate ?: Instant.now().toString().substring(0, 10)
        contabo.cancelDomain(domain.domainName, "Other", date)
        domain.status = DomainStatus.CANCELLED
        return domainRepo.save(domain)
    }

    fun adminRevokeDomainCancellation(domainId: String): Domain {
        val domain =
                domainRepo.findByIdOrNull(domainId) ?: throw NotFoundException("Domain", domainId)
        contabo.revokeDomainCancellation(domain.domainName)
        domain.status = DomainStatus.ACTIVE
        return domainRepo.save(domain)
    }

    fun adminGetDomainAuthCode(domainId: String): Map<String, String> {
        val domain =
                domainRepo.findByIdOrNull(domainId) ?: throw NotFoundException("Domain", domainId)
        val code = contabo.generateAuthCode(domain.domainName)
        domain.authCode = EncryptionUtil.encrypt(code, encryptionKey)
        domainRepo.save(domain)
        return mapOf("authCode" to code)
    }

    fun adminListDomainHandles(userId: String) = domainHandleRepo.findByUserId(userId)

    fun adminCreateDomainHandle(userId: String, dto: Map<String, Any>): DomainHandle {
        val req = mapToHandleRequest(dto)
        val ch = contabo.createDomainHandle(req)
        return domainHandleRepo.save(
                DomainHandle(
                        userId = userId,
                        contaboHandleId = ch.handleId,
                        handleType = ch.handleType.name,
                        firstName = ch.firstName,
                        lastName = ch.lastName,
                        organization = ch.organization,
                        email = ch.email,
                        gender = ch.gender?.name,
                        birthInfo = null,
                        address =
                                mapOf(
                                        "street" to (ch.address?.street ?: ""),
                                        "city" to (ch.address?.city ?: ""),
                                        "country" to (ch.address?.country ?: ""),
                                        "postalCode" to (ch.address?.zipCode ?: ""),
                                ),
                        phone =
                                mapOf(
                                        "countryCode" to (ch.phone?.countryCode ?: ""),
                                        "areaCode" to (ch.phone?.areaCode ?: ""),
                                        "subscriberNumber" to (ch.phone?.subscriberNumber ?: ""),
                                ),
                )
        )
    }

    fun adminUpdateDomainHandle(handleId: String, dto: Map<String, Any>): DomainHandle {
        val handle =
                domainHandleRepo.findByIdOrNull(handleId)
                        ?: throw NotFoundException("Handle", handleId)
        contabo.updateDomainHandle(handle.contaboHandleId!!, mapToHandleRequest(dto))
        dto["firstName"]?.let { handle.firstName = it as String }
        dto["lastName"]?.let { handle.lastName = it as String }
        dto["email"]?.let { handle.email = it as String }
        dto["organization"]?.let { handle.organization = it as String? }
        return domainHandleRepo.save(handle)
    }

    fun adminDeleteDomainHandle(handleId: String): Map<String, String> {
        val handle =
                domainHandleRepo.findByIdOrNull(handleId)
                        ?: throw NotFoundException("Handle", handleId)
        contabo.deleteDomainHandle(handle.contaboHandleId!!)
        domainHandleRepo.delete(handle)
        return mapOf("message" to "Handle deleted")
    }

    // ════════════════════════════════════════════════════════════
    // Admin Proxy: Object Storage actions
    // ════════════════════════════════════════════════════════════

    fun adminGetStorageCredentials(storageId: String): Map<String, Any?> {
        val storage =
                objectStorageRepo.findByIdOrNull(storageId)
                        ?: throw NotFoundException("ObjectStorage", storageId)
        val creds = contabo.getS3Credentials(storage.userId)
        return mapOf(
                "accessKey" to creds.accessKey,
                "secretKey" to creds.secretKey,
                "endpoint" to creds.s3Url,
                "region" to creds.region,
                "bucket" to (storage.displayName ?: "storage-$storageId"),
        )
    }

    fun adminUpgradeStorage(storageId: String, dto: UpgradeStorageDto): ObjectStorage {
        val storage =
                objectStorageRepo.findByIdOrNull(storageId)
                        ?: throw NotFoundException("ObjectStorage", storageId)
        contabo.upgradeObjectStorage(
                storage.contaboStorageId,
                ContaboUpgradeObjectStorageRequest(
                        totalPurchasedSpaceTB = dto.totalPurchasedSpaceTB,
                        autoScaling =
                                dto.autoScaling?.let {
                                    ContaboAutoScaling(
                                            state =
                                                    try {
                                                        AutoScalingState.valueOf(
                                                                (it["state"] as? String)
                                                                        ?.uppercase()
                                                                        ?: "ENABLED"
                                                        )
                                                    } catch (_: Exception) {
                                                        AutoScalingState.ENABLED
                                                    },
                                            sizeLimitTB = (it["sizeLimitTB"] as? Number)?.toDouble()
                                                            ?: dto.totalPurchasedSpaceTB ?: 1.0
                                    )
                                }
                )
        )
        storage.status = ObjectStorageStatus.UPGRADING
        dto.totalPurchasedSpaceTB?.let { storage.totalPurchasedSpaceTB = it }
        return objectStorageRepo.save(storage)
    }

    fun adminCancelStorage(storageId: String, dto: CancelStorageDto): ObjectStorage {
        val storage =
                objectStorageRepo.findByIdOrNull(storageId)
                        ?: throw NotFoundException("ObjectStorage", storageId)
        val date = dto.cancelDate ?: Instant.now().plusSeconds(86400).toString().substring(0, 10)
        contabo.cancelObjectStorage(storage.contaboStorageId, date)
        storage.status = ObjectStorageStatus.CANCELLED
        return objectStorageRepo.save(storage)
    }

    // ════════════════════════════════════════════════════════════
    // Admin Proxy: Firewall actions
    // ════════════════════════════════════════════════════════════

    fun adminCreateFirewall(userId: String, dto: Map<String, Any>): Firewall {
        val name = dto["name"] as String
        val req =
                ContaboCreateFirewallRequest(
                        name = name,
                        description = dto["description"] as? String,
                        status = FirewallStatus.ACTIVE,
                        rules = null,
                )
        val cf = contabo.createFirewall(req)
        return firewallRepo.save(
                Firewall(
                        userId = userId,
                        contaboFirewallId = cf.firewallId,
                        name = dto["name"] as? String ?: "",
                        description = dto["description"] as? String,
                )
        )
    }

    fun adminUpdateFirewall(firewallId: String, dto: Map<String, Any>): Firewall {
        val fw =
                firewallRepo.findByIdOrNull(firewallId)
                        ?: throw NotFoundException("Firewall", firewallId)
        contabo.updateFirewall(
                fw.contaboFirewallId,
                ContaboUpdateFirewallRequest(name = dto["name"] as? String)
        )
        (dto["name"] as? String)?.let { fw.name = it }
        dto["description"]?.let { fw.description = it as? String }
        return firewallRepo.save(fw)
    }

    fun adminUpdateFirewallRules(firewallId: String, dto: Map<String, Any>): Firewall {
        val fw =
                firewallRepo.findByIdOrNull(firewallId)
                        ?: throw NotFoundException("Firewall", firewallId)
        contabo.updateFirewallRules(
                fw.contaboFirewallId,
                ContaboUpdateFirewallRulesRequest(
                        rules =
                                ContaboFirewallRules(
                                        inbound =
                                                (dto["inbound"] as? List<*>)?.map {
                                                    @Suppress("UNCHECKED_CAST")
                                                    val r = it as Map<String, Any>
                                                    ContaboFirewallRule(
                                                            protocol =
                                                                    try {
                                                                        FirewallProtocol.valueOf(
                                                                                (r["protocol"] as?
                                                                                                String)
                                                                                        ?.uppercase()
                                                                                        ?: "TCP"
                                                                        )
                                                                    } catch (_: Exception) {
                                                                        FirewallProtocol.TCP
                                                                    },
                                                            port = (r["port"] as? Number)?.toInt(),
                                                            portRange = r["portRange"] as? String,
                                                            sourceIp = r["sourceIp"] as? String,
                                                            sourceNet = r["sourceNet"] as? String,
                                                            action =
                                                                    try {
                                                                        FirewallAction.valueOf(
                                                                                (r["action"] as?
                                                                                                String)
                                                                                        ?.uppercase()
                                                                                        ?: "ALLOW"
                                                                        )
                                                                    } catch (_: Exception) {
                                                                        FirewallAction.ALLOW
                                                                    },
                                                    )
                                                }
                                )
                )
        )
        firewallRuleRepo.findByFirewallId(firewallId).forEach { firewallRuleRepo.delete(it) }
        @Suppress("UNCHECKED_CAST")
        (dto["inbound"] as? List<Map<String, Any>>)?.forEach { r ->
            firewallRuleRepo.save(
                    FirewallRule(
                            firewallId = firewallId,
                            protocol = r["protocol"] as? String ?: "tcp",
                            port = (r["port"] as? Number)?.toInt(),
                            portRange = r["portRange"] as? String,
                            sourceIp = r["sourceIp"] as? String,
                            sourceNet = r["sourceNet"] as? String,
                            action = r["action"] as? String ?: "allow",
                    )
            )
        }
        return fw
    }

    fun adminDeleteFirewall(firewallId: String): Map<String, String> {
        val fw =
                firewallRepo.findByIdOrNull(firewallId)
                        ?: throw NotFoundException("Firewall", firewallId)
        contabo.deleteFirewall(fw.contaboFirewallId)
        firewallRuleRepo.findByFirewallId(firewallId).forEach { firewallRuleRepo.delete(it) }
        firewallAssignmentRepo.findByFirewallId(firewallId).forEach {
            firewallAssignmentRepo.delete(it)
        }
        firewallRepo.delete(fw)
        return mapOf("message" to "Firewall deleted")
    }

    fun adminAssignFirewallToServer(firewallId: String, serverId: String): FirewallAssignment {
        val fw =
                firewallRepo.findByIdOrNull(firewallId)
                        ?: throw NotFoundException("Firewall", firewallId)
        val server =
                serverRepo.findByIdOrNull(serverId) ?: throw NotFoundException("Server", serverId)
        contabo.assignInstanceToFirewall(fw.contaboFirewallId, server.contaboInstanceId.toLong())
        return firewallAssignmentRepo.save(
                FirewallAssignment(firewallId = firewallId, serverId = serverId)
        )
    }

    fun adminUnassignFirewallFromServer(firewallId: String, serverId: String): Map<String, String> {
        val fw =
                firewallRepo.findByIdOrNull(firewallId)
                        ?: throw NotFoundException("Firewall", firewallId)
        val server =
                serverRepo.findByIdOrNull(serverId) ?: throw NotFoundException("Server", serverId)
        contabo.unassignInstanceFromFirewall(
                fw.contaboFirewallId,
                server.contaboInstanceId.toLong()
        )
        firewallAssignmentRepo
                .findByFirewallId(firewallId)
                .filter { it.serverId == serverId }
                .forEach { firewallAssignmentRepo.delete(it) }
        return mapOf("message" to "Server unassigned from firewall")
    }

    // ════════════════════════════════════════════════════════════
    // Admin Proxy: Private Network actions
    // ════════════════════════════════════════════════════════════

    fun adminCreatePrivateNetwork(userId: String, dto: Map<String, Any>): PrivateNetwork {
        val req =
                ContaboCreatePrivateNetworkRequest(
                        region = dto["region"] as? String ?: "EU",
                        name = dto["name"] as String,
                        description = dto["description"] as? String,
                )
        val cn = contabo.createPrivateNetwork(req)
        return privateNetworkRepo.save(
                PrivateNetwork(
                        userId = userId,
                        contaboNetworkId = cn.privateNetworkId.toString(),
                        name = cn.name ?: "",
                        description = cn.description,
                        region = cn.region ?: "EU",
                        dataCenter = cn.dataCenter,
                        cidr = cn.cidr,
                )
        )
    }

    fun adminUpdatePrivateNetwork(networkId: String, dto: Map<String, Any>): PrivateNetwork {
        val net =
                privateNetworkRepo.findByIdOrNull(networkId)
                        ?: throw NotFoundException("PrivateNetwork", networkId)
        contabo.updatePrivateNetwork(
                net.contaboNetworkId.toLong(),
                ContaboUpdatePrivateNetworkRequest(
                        name = dto["name"] as? String,
                        description = dto["description"] as? String
                )
        )
        (dto["name"] as? String)?.let { net.name = it }
        dto["description"]?.let { net.description = it as? String }
        return privateNetworkRepo.save(net)
    }

    fun adminDeletePrivateNetwork(networkId: String): Map<String, String> {
        val net =
                privateNetworkRepo.findByIdOrNull(networkId)
                        ?: throw NotFoundException("PrivateNetwork", networkId)
        val assignments = privateNetworkAssignmentRepo.findByPrivateNetworkId(networkId)
        if (assignments.isNotEmpty())
                throw BadRequestException(
                        "Cannot delete network with assigned servers. Unassign all first."
                )
        contabo.deletePrivateNetwork(net.contaboNetworkId.toLong())
        privateNetworkRepo.delete(net)
        return mapOf("message" to "Private network deleted")
    }

    fun adminAssignServerToNetwork(networkId: String, serverId: String): PrivateNetworkAssignment {
        val net =
                privateNetworkRepo.findByIdOrNull(networkId)
                        ?: throw NotFoundException("PrivateNetwork", networkId)
        val server =
                serverRepo.findByIdOrNull(serverId) ?: throw NotFoundException("Server", serverId)
        contabo.assignInstanceToPrivateNetwork(
                net.contaboNetworkId.toLong(),
                server.contaboInstanceId.toLong()
        )
        return privateNetworkAssignmentRepo.save(
                PrivateNetworkAssignment(privateNetworkId = networkId, serverId = serverId)
        )
    }

    fun adminUnassignServerFromNetwork(networkId: String, serverId: String): Map<String, String> {
        val net =
                privateNetworkRepo.findByIdOrNull(networkId)
                        ?: throw NotFoundException("PrivateNetwork", networkId)
        val server =
                serverRepo.findByIdOrNull(serverId) ?: throw NotFoundException("Server", serverId)
        contabo.unassignInstanceFromPrivateNetwork(
                net.contaboNetworkId.toLong(),
                server.contaboInstanceId.toLong()
        )
        privateNetworkAssignmentRepo
                .findByPrivateNetworkId(networkId)
                .filter { it.serverId == serverId }
                .forEach { privateNetworkAssignmentRepo.delete(it) }
        return mapOf("message" to "Server unassigned from private network")
    }

    // ════════════════════════════════════════════════════════════
    // Admin Proxy: VIP actions
    // ════════════════════════════════════════════════════════════

    fun adminSyncVips(userId: String): Map<String, Int> {
        val vips = contabo.listVips()
        for (v in vips) {
            val existing = vipRepo.findByIp(v.ip)
            if (existing != null) {
                existing.resourceId = v.resourceId?.toString()
                existing.resourceType = v.resourceType
                existing.dataCenter = v.dataCenter
                existing.region = v.region ?: "EU"
                vipRepo.save(existing)
            } else {
                vipRepo.save(
                        Vip(
                                userId = userId,
                                ip = v.ip,
                                resourceId = v.resourceId?.toString(),
                                resourceType = v.resourceType,
                                ipVersion = v.ipVersion,
                                type = v.type.name,
                                dataCenter = v.dataCenter,
                                region = v.region ?: "EU"
                        )
                )
            }
        }
        return mapOf("synced" to vips.size)
    }

    fun adminAssignVipToServer(ip: String, serverId: String): Vip {
        val vip = vipRepo.findByIp(ip) ?: throw NotFoundException("Vip", ip)
        val server =
                serverRepo.findByIdOrNull(serverId) ?: throw NotFoundException("Server", serverId)
        contabo.assignVipToInstance(ip, "instances", server.contaboInstanceId.toLong())
        vip.resourceId = server.id
        vip.resourceType = "instance"
        vip.type = VipType.ADDITIONAL.name
        return vipRepo.save(vip)
    }

    fun adminUnassignVipFromServer(ip: String, serverId: String): Vip {
        val vip = vipRepo.findByIp(ip) ?: throw NotFoundException("Vip", ip)
        val server =
                serverRepo.findByIdOrNull(serverId) ?: throw NotFoundException("Server", serverId)
        contabo.unassignVipFromInstance(ip, "instances", server.contaboInstanceId.toLong())
        vip.resourceId = null
        vip.resourceType = null
        return vipRepo.save(vip)
    }

    // ════════════════════════════════════════════════════════════
    // Admin Proxy: Secrets
    // ════════════════════════════════════════════════════════════

    fun adminListSecrets(userId: String) = secretRepo.findByUserId(userId)

    fun adminCreateSecret(userId: String, dto: Map<String, Any>): Secret {
        val cs =
                contabo.createSecret(
                        ContaboCreateSecretRequest(
                                name = dto["name"] as String,
                                type =
                                        try {
                                            ContaboSecretType.valueOf(dto["type"] as String)
                                        } catch (_: Exception) {
                                            ContaboSecretType.password
                                        },
                                value = dto["value"] as String,
                        )
                )
        return secretRepo.save(
                Secret(
                        userId = userId,
                        contaboId = cs.secretId.toInt(),
                        name = cs.name,
                        type = cs.type.name
                )
        )
    }

    fun adminDeleteSecret(secretId: String): Map<String, String> {
        val sec = secretRepo.findByIdOrNull(secretId) ?: throw NotFoundException("Secret", secretId)
        contabo.deleteSecret(sec.contaboId.toLong())
        secretRepo.delete(sec)
        return mapOf("message" to "Secret deleted")
    }

    fun adminSyncSecrets(userId: String): Map<String, Int> {
        val secrets = contabo.listSecrets()
        for (cs in secrets) {
            val existing = secretRepo.findByContaboId(cs.secretId.toInt())
            if (existing != null) {
                existing.name = cs.name
                existing.type = cs.type.name
                secretRepo.save(existing)
            } else {
                secretRepo.save(
                        Secret(
                                userId = userId,
                                contaboId = cs.secretId.toInt(),
                                name = cs.name,
                                type = cs.type.name
                        )
                )
            }
        }
        return mapOf("synced" to secrets.size)
    }

    // ════════════════════════════════════════════════════════════
    // Admin Proxy: Tags
    // ════════════════════════════════════════════════════════════

    fun adminListTags(userId: String) = tagRepo.findByUserId(userId)

    fun adminCreateTag(userId: String, dto: Map<String, Any>): Tag {
        val color = dto["color"] as? String ?: "#6366f1"
        val ct =
                contabo.createTag(
                        ContaboCreateTagRequest(
                                name = dto["name"] as String,
                                color = color,
                        )
                )
        return tagRepo.save(
                Tag(userId = userId, contaboId = ct.tagId.toInt(), name = ct.name, color = ct.color)
        )
    }

    fun adminDeleteTag(tagId: String): Map<String, String> {
        val tag = tagRepo.findByIdOrNull(tagId) ?: throw NotFoundException("Tag", tagId)
        contabo.deleteTag(tag.contaboId.toLong())
        tagRepo.delete(tag)
        return mapOf("message" to "Tag deleted")
    }

    fun adminSyncTags(userId: String): Map<String, Int> {
        val tags = contabo.listTags()
        for (ct in tags) {
            val existing = tagRepo.findByContaboId(ct.tagId.toInt())
            if (existing != null) {
                existing.name = ct.name ?: ""
                existing.color = ct.color
                tagRepo.save(existing)
            } else {
                tagRepo.save(
                        Tag(
                                userId = userId,
                                contaboId = ct.tagId.toInt(),
                                name = ct.name,
                                color = ct.color
                        )
                )
            }
        }
        return mapOf("synced" to tags.size)
    }

    fun adminAssignTag(tagId: String, resourceType: String, resourceId: String): Any {
        val tag = tagRepo.findByIdOrNull(tagId) ?: throw NotFoundException("Tag", tagId)
        return contabo.createTagAssignment(
                tag.contaboId.toLong(),
                TagResourceType.valueOf(resourceType),
                resourceId
        )
    }

    fun adminUnassignTag(
            tagId: String,
            resourceType: String,
            resourceId: String
    ): Map<String, String> {
        val tag = tagRepo.findByIdOrNull(tagId) ?: throw NotFoundException("Tag", tagId)
        contabo.deleteTagAssignment(
                tag.contaboId.toLong(),
                TagResourceType.valueOf(resourceType),
                resourceId
        )
        return mapOf("message" to "Tag unassigned")
    }

    // ════════════════════════════════════════════════════════════
    // Broadcast Notifications
    // ════════════════════════════════════════════════════════════

    fun sendIncident(dto: SendIncidentDto): Map<String, Int> {
        var sent = 0
        if (dto.userId != null) {
            val user =
                    userRepo.findByIdOrNull(dto.userId)
                            ?: throw NotFoundException("User", dto.userId)
            notifications.sendIncidentEmail(
                    user.email,
                    dto.region,
                    dto.startedAt,
                    dto.services,
                    dto.reference,
                    user.language,
                    user.id
            )
            sent = 1
        } else {
            val servers =
                    serverRepo.findAll().filter { it.region == dto.region }.distinctBy { it.userId }
            for (s in servers) {
                try {
                    val user = userRepo.findByIdOrNull(s.userId) ?: continue
                    notifications.sendIncidentEmail(
                            user.email,
                            dto.region,
                            dto.startedAt,
                            dto.services,
                            dto.reference,
                            user.language,
                            user.id
                    )
                    sent++
                } catch (e: Exception) {
                    logger.error("Incident email failed for user ${s.userId}: ${e.message}")
                }
            }
        }
        return mapOf("sent" to sent)
    }

    fun sendMaintenance(dto: SendMaintenanceDto): Map<String, Int> {
        var sent = 0
        if (dto.userId != null) {
            val user =
                    userRepo.findByIdOrNull(dto.userId)
                            ?: throw NotFoundException("User", dto.userId)
            notifications.sendScheduledMaintenanceEmail(
                    user.email,
                    dto.region,
                    dto.window,
                    dto.localTime,
                    dto.duration,
                    dto.impact,
                    dto.reference,
                    dto.hostname,
                    user.language,
                    user.id
            )
            sent = 1
        } else {
            val servers =
                    serverRepo.findAll().filter { it.region == dto.region }.distinctBy { it.userId }
            for (s in servers) {
                try {
                    val user = userRepo.findByIdOrNull(s.userId) ?: continue
                    notifications.sendScheduledMaintenanceEmail(
                            user.email,
                            dto.region,
                            dto.window,
                            dto.localTime,
                            dto.duration,
                            dto.impact,
                            dto.reference,
                            dto.hostname,
                            user.language,
                            user.id
                    )
                    sent++
                } catch (e: Exception) {
                    logger.error("Maintenance email failed for user ${s.userId}: ${e.message}")
                }
            }
        }
        return mapOf("sent" to sent)
    }

    // ════════════════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════════════════

    @Suppress("UNCHECKED_CAST")
    private fun mapToHandleRequest(dto: Map<String, Any>): ContaboCreateDomainHandleRequest {
        val address = dto["address"] as? Map<String, String>
        val phone = dto["phone"] as? Map<String, String>
        return ContaboCreateDomainHandleRequest(
                handleType =
                        try {
                            HandleType.valueOf(
                                    (dto["handleType"] as? String)?.uppercase() ?: "PERSON"
                            )
                        } catch (_: Exception) {
                            HandleType.PERSON
                        },
                firstName = (dto["firstName"] as? String) ?: "",
                lastName = (dto["lastName"] as? String) ?: "",
                organization = dto["organization"] as? String,
                email = (dto["email"] as? String) ?: "",
                gender =
                        dto["gender"]?.let {
                            try {
                                Gender.valueOf((it as String).uppercase())
                            } catch (_: Exception) {
                                null
                            }
                        },
                birthInfo =
                        if (dto["birthDate"] != null ||
                                        dto["birthCity"] != null ||
                                        dto["birthCountry"] != null
                        ) {
                            ContaboBirthInfo(
                                    date = dto["birthDate"] as? String ?: "",
                                    city = dto["birthCity"] as? String ?: "",
                                    country = dto["birthCountry"] as? String ?: "",
                            )
                        } else null,
                address =
                        ContaboHandleAddress(
                                street = address?.get("street")
                                                ?: address?.get("addressLine1") ?: "",
                                streetNumber = address?.get("streetNumber") ?: "",
                                zipCode = address?.get("postalCode")
                                                ?: address?.get("zipCode") ?: "",
                                city = address?.get("city") ?: "",
                                country = address?.get("country") ?: "",
                        ),
                phone =
                        ContaboHandlePhone(
                                countryCode = phone?.get("countryCode") ?: "",
                                areaCode = phone?.get("areaCode"),
                                subscriberNumber = phone?.get("subscriberNumber") ?: "",
                        ),
                fax = null,
        )
    }

    companion object {
        private val TLD_SEED =
                """
            .com 12.99 14.99 12.99
            .net 13.99 15.99 13.99
            .org 13.99 15.99 13.99
        """.trimIndent()
    }
}