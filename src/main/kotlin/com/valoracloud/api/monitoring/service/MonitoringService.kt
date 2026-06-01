package com.valoracloud.api.monitoring.service

import com.valoracloud.api.common.dto.PaginatedResponse
import com.valoracloud.api.common.exceptions.NotFoundException
import com.valoracloud.api.common.model.MonitorStatus
import com.valoracloud.api.common.utils.EncryptionUtil
import com.valoracloud.api.config.*
import com.valoracloud.api.entity.*
import com.valoracloud.api.monitoring.checkers.*
import com.valoracloud.api.monitoring.dto.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.repository.findByIdOrNull
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MonitoringService(
        private val serverMonitorRepo: ServerMonitorRepository,
        private val serverCheckRepo: ServerCheckRepository,
        private val monitorAlertRepo: MonitorAlertRepository,
        private val uptimeDailyRepo: UptimeDailyRepository,
        private val maintenanceWindowRepo: MaintenanceWindowRepository,
        private val serverRepo: ServerRepository,
        private val alertEngine: AlertEngineService,
        @Value("\${ENCRYPTION_KEY:}") private val encryptionKey: String,
) {
    private val log = LoggerFactory.getLogger(MonitoringService::class.java)
    private val lastCheckMap = ConcurrentHashMap<String, Long>()

    @Scheduled(fixedDelay = 30000) // every 30 seconds; per-monitor interval enforced below
    fun schedulerTick() {
        val now = System.currentTimeMillis()
        val monitors = serverMonitorRepo.findByIsActiveTrue()

        for (monitor in monitors) {
            val last = lastCheckMap.getOrDefault(monitor.id, 0L)
            val intervalMs = monitor.checkIntervalSeconds * 1_000L
            if (now - last >= intervalMs) {
                lastCheckMap[monitor.id] = now
                kotlinx.coroutines.runBlocking { runCheckForMonitor(monitor) }
            }
        }
    }

    private suspend fun runCheckForMonitor(monitor: ServerMonitor) {
        try {
            val server = serverRepo.findByIdOrNull(monitor.serverId) ?: return
            runCheck(monitor, server.hostname, server.ipAddress ?: server.hostname)
        } catch (e: Exception) {
            log.error("Check failed for monitor ${monitor.id}: ${e.message}")
        }
    }

    @Transactional
    suspend fun runCheck(monitor: ServerMonitor, hostname: String, ip: String) {
        val startTime = System.currentTimeMillis()

        // Check if currently in a maintenance window
        val now = Instant.now()
        val maintenances =
                maintenanceWindowRepo.findByMonitorIdAndStartsAtBeforeAndEndsAtAfter(
                        monitor.id,
                        now,
                        now
                )
        val inMaintenance = maintenances.isNotEmpty()

        // Build check data
        val check =
                ServerCheck(
                        monitorId = monitor.id,
                        checkedAt = now,
                        status = MonitorStatus.DOWN,
                        checkerNode = "primary",
                )

        // Protocol check
        when (monitor.protocol) {
            "http", "https" -> {
                val url = monitor.checkUrl ?: "${monitor.protocol}://$ip"
                val result = checkHttp(url)
                check.status = result.status
                check.httpStatusCode = result.httpStatusCode
                check.httpResponseTimeMs = result.httpResponseTimeMs
                check.httpResponseBodySnippet = result.httpResponseBodySnippet
                check.pingMs = result.pingMs
                check.errorMessage = result.errorMessage

                if (monitor.protocol == "https" && !ip.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))
                ) {
                    val sslResult = checkSsl(hostname, monitor.checkPort)
                    check.sslValid = sslResult.sslValid
                    check.sslDaysUntilExpiry = sslResult.sslDaysUntilExpiry
                    check.sslIssuer = sslResult.sslIssuer
                    if (check.errorMessage == null) check.errorMessage = sslResult.errorMessage
                }
            }
            "tcp" -> {
                val result = checkTcp(ip, monitor.checkPort)
                check.status = if (result.tcpOpen) MonitorStatus.UP else MonitorStatus.DOWN
                check.tcpOpen = result.tcpOpen
                check.pingMs = result.pingMs
                check.errorMessage = result.errorMessage
            }
            "icmp" -> {
                val result = checkTcp(ip, 22)
                check.status = if (result.tcpOpen) MonitorStatus.UP else MonitorStatus.DOWN
                check.pingMs = result.pingMs
                check.errorMessage = result.errorMessage
            }
        }

        // DNS check
        if (hostname.isNotBlank() && !hostname.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) {
            val dnsResult = checkDns(hostname)
            check.dnsResolved = dnsResult.dnsResolved
            check.dnsIpResolved = dnsResult.dnsIpResolved
        }

        // Agent check
        if (monitor.agentPort != null) {
            var agentSecret = ""
            if (monitor.agentSecret != null && encryptionKey.isNotBlank()) {
                try {
                    agentSecret = EncryptionUtil.decrypt(monitor.agentSecret!!, encryptionKey)
                } catch (_: Exception) {}
            }
            val agentResult = checkAgent(ip, monitor.agentPort!!, agentSecret)
            if (agentResult.success && agentResult.metrics != null) {
                val m = agentResult.metrics!!
                check.cpuPercent =
                        BigDecimal.valueOf(m.cpu_percent).setScale(2, RoundingMode.HALF_UP)
                check.ramPercent =
                        BigDecimal.valueOf(m.ram_percent).setScale(2, RoundingMode.HALF_UP)
                check.ramUsedMb = m.ram_used_mb
                check.ramTotalMb = m.ram_total_mb
                m.disk_percent?.let {
                    check.diskPercent = BigDecimal.valueOf(it).setScale(2, RoundingMode.HALF_UP)
                }
                m.disk_used_gb?.let {
                    check.diskUsedGb = BigDecimal.valueOf(it).setScale(2, RoundingMode.HALF_UP)
                }
                m.disk_total_gb?.let {
                    check.diskTotalGb = BigDecimal.valueOf(it).setScale(2, RoundingMode.HALF_UP)
                }
                check.loadAvg1m =
                        BigDecimal.valueOf(m.load_avg_1m).setScale(2, RoundingMode.HALF_UP)
                check.loadAvg5m =
                        BigDecimal.valueOf(m.load_avg_5m).setScale(2, RoundingMode.HALF_UP)
                check.loadAvg15m =
                        BigDecimal.valueOf(m.load_avg_15m).setScale(2, RoundingMode.HALF_UP)
                check.processesCount = m.processes_count
                m.network_in_mbps?.let {
                    check.networkInMbps = BigDecimal.valueOf(it).setScale(2, RoundingMode.HALF_UP)
                }
                m.network_out_mbps?.let {
                    check.networkOutMbps = BigDecimal.valueOf(it).setScale(2, RoundingMode.HALF_UP)
                }
                check.openConnections = m.open_connections

                if (check.status == MonitorStatus.UP &&
                                (m.cpu_percent > monitor.alertThresholdCpuPct ||
                                        m.ram_percent > monitor.alertThresholdRamPct)
                ) {
                    check.status = MonitorStatus.DEGRADED
                }
            }
        }

        check.checkDurationMs = (System.currentTimeMillis() - startTime).toInt()

        val savedCheck = serverCheckRepo.save(check)
        upsertUptimeDaily(monitor.id, savedCheck.status)

        if (!inMaintenance) {
            alertEngine.evaluate(monitor, savedCheck)
        }

        log.debug(
                "[${hostname}] status=${savedCheck.status} ping=${savedCheck.pingMs}ms duration=${savedCheck.checkDurationMs}ms"
        )
    }

    private fun upsertUptimeDaily(monitorId: String, status: MonitorStatus) {
        val today = LocalDate.now()
        val existing = uptimeDailyRepo.findByMonitorIdAndDate(monitorId, today)

        val isUp = status == MonitorStatus.UP
        val isDown = status == MonitorStatus.DOWN
        val isDegraded = status == MonitorStatus.DEGRADED

        if (existing == null) {
            uptimeDailyRepo.save(
                    UptimeDaily(
                            monitorId = monitorId,
                            date = today,
                            totalChecks = 1,
                            upChecks = if (isUp) 1 else 0,
                            downChecks = if (isDown) 1 else 0,
                            degradedChecks = if (isDegraded) 1 else 0,
                            uptimePercent = if (isUp) BigDecimal("100.00") else BigDecimal.ZERO,
                            firstDownAt = if (isDown) Instant.now() else null,
                    )
            )
        } else {
            val newTotal = existing.totalChecks + 1
            val newUp = existing.upChecks + (if (isUp) 1 else 0)
            val newDown = existing.downChecks + (if (isDown) 1 else 0)
            val newDegraded = existing.degradedChecks + (if (isDegraded) 1 else 0)
            val uptimePct =
                    BigDecimal(newUp)
                            .multiply(BigDecimal(100))
                            .divide(BigDecimal(newTotal), 2, RoundingMode.HALF_UP)

            existing.totalChecks = newTotal
            existing.upChecks = newUp
            existing.downChecks = newDown
            existing.degradedChecks = newDegraded
            existing.uptimePercent = uptimePct
            existing.firstDownAt = existing.firstDownAt ?: if (isDown) Instant.now() else null
            uptimeDailyRepo.save(existing)
        }
    }

    // ─── CRUD Monitors ──────────────────────────────────────────────────

    fun createMonitor(serverId: String, dto: CreateMonitorDto): ServerMonitor {
        val server =
                serverRepo.findByIdOrNull(serverId) ?: throw NotFoundException("Server", serverId)

        val encryptedSecret = dto.agentSecret?.let { EncryptionUtil.encrypt(it, encryptionKey) }

        return serverMonitorRepo.save(
                ServerMonitor(
                        serverId = serverId,
                        protocol = dto.protocol,
                        checkUrl = dto.checkUrl,
                        checkPort = dto.checkPort,
                        checkIntervalSeconds = dto.checkIntervalSeconds,
                        agentPort = dto.agentPort,
                        agentSecret = encryptedSecret,
                        alertThresholdPingMs = dto.alertThresholdPingMs,
                        alertThresholdCpuPct = dto.alertThresholdCpuPct,
                        alertThresholdRamPct = dto.alertThresholdRamPct,
                        alertThresholdDiskPct = dto.alertThresholdDiskPct,
                        notifyEmail = dto.notifyEmail,
                        notifyTelegramChatId = dto.notifyTelegramChatId,
                        notes = dto.notes,
                )
        )
    }

    fun findAllMonitors(page: Int = 1, limit: Int = 20): PaginatedResponse<Map<String, Any?>> {
        val offset = (page - 1).toLong() * limit
        val total = serverMonitorRepo.count()
        // Simplified - correct JPA pagination would use Pageable
        val monitors =
                serverMonitorRepo
                        .findAll()
                        .sortedByDescending { it.createdAt }
                        .drop(offset.toInt())
                        .take(limit)

        val data =
                monitors.map { m ->
                    val server = serverRepo.findByIdOrNull(m.serverId)
                    val lastCheck =
                            serverCheckRepo.findByMonitorIdOrderByCheckedAtDesc(m.id).firstOrNull()
                    mapOf(
                            "id" to m.id,
                            "serverId" to m.serverId,
                            "isActive" to m.isActive,
                            "protocol" to m.protocol,
                            "checkUrl" to m.checkUrl,
                            "checkPort" to m.checkPort,
                            "checkIntervalSeconds" to m.checkIntervalSeconds,
                            "alertThresholdPingMs" to m.alertThresholdPingMs,
                            "alertThresholdCpuPct" to m.alertThresholdCpuPct,
                            "alertThresholdRamPct" to m.alertThresholdRamPct,
                            "alertThresholdDiskPct" to m.alertThresholdDiskPct,
                            "notifyEmail" to m.notifyEmail,
                            "notifyTelegramChatId" to m.notifyTelegramChatId,
                            "notes" to m.notes,
                            "createdAt" to m.createdAt,
                            "server" to
                                    mapOf(
                                            "hostname" to (server?.hostname ?: ""),
                                            "ipAddress" to (server?.ipAddress),
                                    ),
                            "lastCheck" to
                                    lastCheck?.let {
                                        mapOf(
                                                "status" to it.status,
                                                "pingMs" to it.pingMs,
                                                "checkedAt" to it.checkedAt,
                                        )
                                    },
                    )
                }
        return PaginatedResponse(data, total, page, limit, ((total + limit - 1) / limit).toInt())
    }

    fun findMonitorByServer(serverId: String): Map<String, Any?> {
        val monitor =
                serverMonitorRepo.findByServerId(serverId)
                        ?: throw NotFoundException("Monitor", "for server $serverId")
        val server = serverRepo.findByIdOrNull(serverId)
        val lastCheck =
                serverCheckRepo.findByMonitorIdOrderByCheckedAtDesc(monitor.id).firstOrNull()
        // Strip encrypted secret
        return mapOf(
                "id" to monitor.id,
                "serverId" to monitor.serverId,
                "isActive" to monitor.isActive,
                "protocol" to monitor.protocol,
                "checkUrl" to monitor.checkUrl,
                "checkPort" to monitor.checkPort,
                "checkIntervalSeconds" to monitor.checkIntervalSeconds,
                "agentPort" to monitor.agentPort,
                "alertThresholdPingMs" to monitor.alertThresholdPingMs,
                "alertThresholdCpuPct" to monitor.alertThresholdCpuPct,
                "alertThresholdRamPct" to monitor.alertThresholdRamPct,
                "alertThresholdDiskPct" to monitor.alertThresholdDiskPct,
                "notifyEmail" to monitor.notifyEmail,
                "notifyTelegramChatId" to monitor.notifyTelegramChatId,
                "notes" to monitor.notes,
                "server" to
                        mapOf(
                                "hostname" to (server?.hostname ?: ""),
                                "ipAddress" to (server?.ipAddress),
                                "status" to (server?.status),
                        ),
                "lastCheck" to
                        lastCheck?.let {
                            mapOf(
                                    "status" to it.status,
                                    "pingMs" to it.pingMs,
                                    "checkedAt" to it.checkedAt,
                            )
                        },
        )
    }

    fun updateMonitor(serverId: String, dto: UpdateMonitorDto): Map<String, Any?> {
        val monitor =
                serverMonitorRepo.findByServerId(serverId)
                        ?: throw NotFoundException("Monitor", "for server $serverId")

        val newAgentSecret = dto.agentSecret
        val encryptedSecret: String? =
                when {
                    newAgentSecret != null -> EncryptionUtil.encrypt(newAgentSecret, encryptionKey)
                    else -> monitor.agentSecret
                }
        val resolvedSecret = encryptedSecret

        monitor.isActive = dto.isActive ?: monitor.isActive
        monitor.protocol = dto.protocol ?: monitor.protocol
        monitor.checkUrl = dto.checkUrl ?: monitor.checkUrl
        monitor.checkPort = dto.checkPort ?: monitor.checkPort
        monitor.checkIntervalSeconds = dto.checkIntervalSeconds ?: monitor.checkIntervalSeconds
        monitor.agentPort = dto.agentPort ?: monitor.agentPort
        monitor.agentSecret = resolvedSecret
        monitor.alertThresholdPingMs = dto.alertThresholdPingMs ?: monitor.alertThresholdPingMs
        monitor.alertThresholdCpuPct = dto.alertThresholdCpuPct ?: monitor.alertThresholdCpuPct
        monitor.alertThresholdRamPct = dto.alertThresholdRamPct ?: monitor.alertThresholdRamPct
        monitor.alertThresholdDiskPct = dto.alertThresholdDiskPct ?: monitor.alertThresholdDiskPct
        monitor.notifyEmail = dto.notifyEmail ?: monitor.notifyEmail
        monitor.notifyTelegramChatId = dto.notifyTelegramChatId ?: monitor.notifyTelegramChatId
        monitor.notes = dto.notes ?: monitor.notes
        val updated = serverMonitorRepo.save(monitor)

        return mapOf(
                "id" to updated.id,
                "serverId" to updated.serverId,
                "isActive" to updated.isActive,
                "protocol" to updated.protocol,
        )
    }

    fun deleteMonitor(serverId: String): Map<String, Boolean> {
        val monitor =
                serverMonitorRepo.findByServerId(serverId)
                        ?: throw NotFoundException("Monitor", "for server $serverId")
        serverMonitorRepo.delete(monitor)
        return mapOf("deleted" to true)
    }

    // ─── Checks ────────────────────────────────────────────────────────

    fun getChecks(monitorId: String, dto: ChecksQueryDto): PaginatedResponse<ServerCheck> {
        val offset = (dto.page - 1).toLong() * dto.limit
        var checks = serverCheckRepo.findByMonitorIdOrderByCheckedAtDesc(monitorId)

        dto.from?.let { from -> checks = checks.filter { it.checkedAt >= Instant.parse(from) } }
        dto.to?.let { to -> checks = checks.filter { it.checkedAt <= Instant.parse(to) } }
        dto.status?.let { status -> checks = checks.filter { it.status.name == status } }

        val total = checks.size.toLong()
        val paged = checks.drop(offset.toInt()).take(dto.limit)

        return PaginatedResponse(
                paged,
                total,
                dto.page,
                dto.limit,
                ((total + dto.limit - 1) / dto.limit).toInt()
        )
    }

    suspend fun triggerManualCheck(serverId: String): Map<String, Boolean> {
        val monitor =
                serverMonitorRepo.findByServerId(serverId)
                        ?: throw NotFoundException("Monitor", "for server $serverId")
        val server =
                serverRepo.findByIdOrNull(serverId) ?: throw NotFoundException("Server", serverId)
        runCheck(monitor, server.hostname, server.ipAddress ?: server.hostname)
        return mapOf("triggered" to true)
    }

    // ─── Uptime ────────────────────────────────────────────────────────

    fun getUptime(serverId: String, days: Int = 30): Map<String, Any?> {
        val monitor =
                serverMonitorRepo.findByServerId(serverId)
                        ?: throw NotFoundException("Monitor", "for server $serverId")

        val from = LocalDate.now().minusDays(days.toLong())
        val rows =
                uptimeDailyRepo
                        .findByMonitorIdOrderByDateDesc(monitor.id)
                        .filter { it.date >= from }
                        .sortedBy { it.date }

        val totalChecks = rows.sumOf { it.totalChecks }
        val upChecks = rows.sumOf { it.upChecks }
        val totalDowntime = rows.sumOf { it.totalDowntimeSeconds }
        val uptimePct =
                if (totalChecks > 0) (upChecks.toDouble() / totalChecks.toDouble()) * 100.0
                else 100.0

        return mapOf(
                "uptimePercent" to String.format("%.4f", uptimePct).toDouble(),
                "totalDowntimeSeconds" to totalDowntime,
                "meetsSla99_9" to (uptimePct >= 99.9),
                "meetsSla99_5" to (uptimePct >= 99.5),
                "days" to rows,
        )
    }

    // ─── Alerts ────────────────────────────────────────────────────────

    fun getAlerts(
            monitorId: String,
            onlyActive: Boolean = false,
            page: Int = 1,
            limit: Int = 50
    ): PaginatedResponse<MonitorAlert> {
        val offset = (page - 1).toLong() * limit
        val allAlerts =
                if (onlyActive)
                        monitorAlertRepo.findByMonitorIdAndIsResolvedOrderByTriggeredAtDesc(
                                monitorId,
                                false
                        )
                else monitorAlertRepo.findByMonitorIdOrderByTriggeredAtDesc(monitorId)

        val total = allAlerts.size.toLong()
        val paged = allAlerts.drop(offset.toInt()).take(limit)
        return PaginatedResponse(paged, total, page, limit, ((total + limit - 1) / limit).toInt())
    }

    fun getActiveAlertsOverview(): List<MonitorAlert> {
        return monitorAlertRepo.findByIsResolvedAndNotifiedEmail(false, false)
    }

    fun ackAlert(alertId: String, dto: AckAlertDto): MonitorAlert {
        val alert =
                monitorAlertRepo.findByIdOrNull(alertId)
                        ?: throw NotFoundException("Alert", alertId)
        alert.ackAt = Instant.now()
        alert.ackNote = dto.note
        return monitorAlertRepo.save(alert)
    }

    fun resolveAlert(alertId: String): MonitorAlert {
        val alert =
                monitorAlertRepo.findByIdOrNull(alertId)
                        ?: throw NotFoundException("Alert", alertId)
        alert.isResolved = true
        alert.resolvedAt = Instant.now()
        return monitorAlertRepo.save(alert)
    }

    // ─── Maintenance Windows ───────────────────────────────────────────

    fun createMaintenance(
            serverId: String,
            dto: CreateMaintenanceWindowDto,
            createdBy: String
    ): MaintenanceWindow {
        val monitor =
                serverMonitorRepo.findByServerId(serverId)
                        ?: throw NotFoundException("Monitor", "for server $serverId")

        return maintenanceWindowRepo.save(
                MaintenanceWindow(
                        monitorId = monitor.id,
                        title = dto.title,
                        startsAt = Instant.parse(dto.startsAt),
                        endsAt = Instant.parse(dto.endsAt),
                        suppressAlerts = dto.suppressAlerts,
                        createdBy = createdBy,
                )
        )
    }

    fun listMaintenances(serverId: String): List<MaintenanceWindow> {
        val monitor =
                serverMonitorRepo.findByServerId(serverId)
                        ?: throw NotFoundException("Monitor", "for server $serverId")
        return maintenanceWindowRepo.findByMonitorId(monitor.id)
    }

    fun deleteMaintenance(maintenanceId: String): Map<String, Boolean> {
        val mw =
                maintenanceWindowRepo.findByIdOrNull(maintenanceId)
                        ?: throw NotFoundException("MaintenanceWindow", maintenanceId)
        maintenanceWindowRepo.delete(mw)
        return mapOf("deleted" to true)
    }

    // ─── Overview ──────────────────────────────────────────────────────

    fun getOverview(): Map<String, Any?> {
        val monitors = serverMonitorRepo.findByIsActiveTrue()
        val statuses =
                monitors.map { m ->
                    serverCheckRepo.findByMonitorIdOrderByCheckedAtDesc(m.id).firstOrNull()?.status
                            ?: "UNKNOWN"
                }

        val up = statuses.count { it == MonitorStatus.UP }
        val down = statuses.count { it == MonitorStatus.DOWN }
        val degraded = statuses.count { it == MonitorStatus.DEGRADED }
        val timeout = statuses.count { it == MonitorStatus.TIMEOUT }
        val unknown = statuses.count { it == "UNKNOWN" }
        val activeAlerts =
                monitorAlertRepo
                        .findByIsResolvedAndNotifiedEmail(false, false)
                        .size
                        .toLong() // This is approximate; ideally count unresolved

        return mapOf(
                "total" to monitors.size,
                "up" to up,
                "down" to down,
                "degraded" to degraded,
                "timeout" to timeout,
                "unknown" to unknown,
                "activeAlerts" to
                        (monitorAlertRepo.findByMonitorIdOrderByTriggeredAtDesc(
                                        monitors.firstOrNull()?.id ?: ""
                                )
                                .count { !it.isResolved }),
        )
    }
}
