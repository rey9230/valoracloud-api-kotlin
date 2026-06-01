package com.valoracloud.api.monitoring.controller

import com.valoracloud.api.monitoring.dto.*
import com.valoracloud.api.monitoring.service.MonitoringService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/admin/monitoring")
@PreAuthorize("hasRole('ADMIN')")
class MonitoringController(
    private val monitoringService: MonitoringService,
) {
    // ─── Overview ──────────────────────────────────────────────────────

    @GetMapping("/overview")
    fun getOverview() = monitoringService.getOverview()

    @GetMapping("/alerts/active")
    fun getActiveAlerts() = monitoringService.getActiveAlertsOverview()

    // ─── Monitors CRUD ────────────────────────────────────────────────

    @GetMapping
    fun findAll(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") limit: Int,
    ) = monitoringService.findAllMonitors(page, limit)

    @GetMapping("/servers/{serverId}")
    fun findOne(@PathVariable serverId: String) = monitoringService.findMonitorByServer(serverId)

    @PostMapping("/servers/{serverId}")
    fun create(
        @PathVariable serverId: String,
        @RequestBody dto: CreateMonitorDto,
    ) = monitoringService.createMonitor(serverId, dto)

    @PatchMapping("/servers/{serverId}")
    fun update(
        @PathVariable serverId: String,
        @RequestBody dto: UpdateMonitorDto,
    ) = monitoringService.updateMonitor(serverId, dto)

    @DeleteMapping("/servers/{serverId}")
    fun remove(@PathVariable serverId: String) = monitoringService.deleteMonitor(serverId)

    @PostMapping("/servers/{serverId}/check")
    suspend fun triggerCheck(@PathVariable serverId: String) =
        monitoringService.triggerManualCheck(serverId)

    // ─── Checks history ───────────────────────────────────────────────

    @GetMapping("/servers/{serverId}/checks")
    fun getChecks(
        @PathVariable serverId: String,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(required = false) status: String?,
    ): Any {
        val monitor = monitoringService.findMonitorByServer(serverId)
        val monitorId = monitor["id"] as String
        return monitoringService.getChecks(monitorId, ChecksQueryDto(
            page = page ?: 1, limit = limit ?: 100, from = from, to = to, status = status))
    }

    // ─── Uptime ───────────────────────────────────────────────────────

    @GetMapping("/servers/{serverId}/uptime")
    fun getUptime(
        @PathVariable serverId: String,
        @RequestParam(defaultValue = "30") days: Int,
    ) = monitoringService.getUptime(serverId, days)

    // ─── Alerts ───────────────────────────────────────────────────────

    @GetMapping("/servers/{serverId}/alerts")
    fun getAlerts(
        @PathVariable serverId: String,
        @RequestParam(defaultValue = "false") onlyActive: Boolean,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Any {
        val monitor = monitoringService.findMonitorByServer(serverId)
        val monitorId = monitor["id"] as String
        return monitoringService.getAlerts(monitorId, onlyActive, page, limit)
    }

    @PatchMapping("/alerts/{alertId}/ack")
    fun ackAlert(
        @PathVariable alertId: String,
        @RequestBody dto: AckAlertDto,
    ) = monitoringService.ackAlert(alertId, dto)

    @PatchMapping("/alerts/{alertId}/resolve")
    fun resolveAlert(@PathVariable alertId: String) =
        monitoringService.resolveAlert(alertId)

    // ─── Maintenance Windows ──────────────────────────────────────────

    @GetMapping("/servers/{serverId}/maintenances")
    fun listMaintenances(@PathVariable serverId: String) =
        monitoringService.listMaintenances(serverId)

    @PostMapping("/servers/{serverId}/maintenances")
    fun createMaintenance(
        @PathVariable serverId: String,
        @RequestBody dto: CreateMaintenanceWindowDto,
    ) = monitoringService.createMaintenance(serverId, dto, "admin") // TODO: get from auth context

    @DeleteMapping("/maintenances/{maintenanceId}")
    fun deleteMaintenance(@PathVariable maintenanceId: String) =
        monitoringService.deleteMaintenance(maintenanceId)
}
