package com.valoracloud.api.monitoring.service

import com.valoracloud.api.common.model.*
import com.valoracloud.api.config.MonitorAlertRepository
import com.valoracloud.api.entity.MonitorAlert
import com.valoracloud.api.entity.ServerCheck
import com.valoracloud.api.entity.ServerMonitor
import com.valoracloud.api.notifications.service.NotificationsService
import java.math.BigDecimal
import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AlertEngineService(
        private val monitorAlertRepo: MonitorAlertRepository,
        private val notifications: NotificationsService,
        private val telegram: TelegramNotifyService,
) {
    private val log = LoggerFactory.getLogger(AlertEngineService::class.java)

    data class AlertCondition(
            val type: MonitorAlertType,
            val severity: MonitorAlertSeverity,
            val message: String,
            val value: BigDecimal?,
            val threshold: BigDecimal?,
    )

    @Transactional
    suspend fun evaluate(monitor: ServerMonitor, check: ServerCheck) {
        val conditions = mutableListOf<AlertCondition>()

        // 1. Server down / timeout
        if (check.status == MonitorStatus.DOWN || check.status == MonitorStatus.TIMEOUT) {
            conditions.add(
                    AlertCondition(
                            MonitorAlertType.DOWN,
                            MonitorAlertSeverity.CRITICAL,
                            "Servidor sin respuesta — ${check.errorMessage ?: "sin respuesta"}",
                            null,
                            null
                    )
            )
        }

        // 2. Degraded
        if (check.status == MonitorStatus.DEGRADED) {
            conditions.add(
                    AlertCondition(
                            MonitorAlertType.DEGRADED,
                            MonitorAlertSeverity.WARNING,
                            "Servidor degradado — HTTP ${check.httpStatusCode ?: "error"}",
                            check.httpStatusCode?.let { BigDecimal(it) },
                            null
                    )
            )
        }

        // 3. High latency
        val pingMs = check.pingMs
        if (pingMs != null && pingMs > monitor.alertThresholdPingMs) {
            conditions.add(
                    AlertCondition(
                            MonitorAlertType.HIGH_LATENCY,
                            MonitorAlertSeverity.WARNING,
                            "Latencia alta: ${pingMs}ms (umbral: ${monitor.alertThresholdPingMs}ms)",
                            BigDecimal(pingMs),
                            BigDecimal(monitor.alertThresholdPingMs)
                    )
            )
        }

        // 4. High CPU
        if (check.cpuPercent != null) {
            val cpu = check.cpuPercent!!.toDouble()
            if (cpu > monitor.alertThresholdCpuPct) {
                conditions.add(
                        AlertCondition(
                                MonitorAlertType.HIGH_CPU,
                                if (cpu > 95) MonitorAlertSeverity.CRITICAL
                                else MonitorAlertSeverity.WARNING,
                                "CPU al ${cpu}% (umbral: ${monitor.alertThresholdCpuPct}%)",
                                BigDecimal(cpu),
                                BigDecimal(monitor.alertThresholdCpuPct)
                        )
                )
            }
        }

        // 5. High RAM
        if (check.ramPercent != null) {
            val ram = check.ramPercent!!.toDouble()
            if (ram > monitor.alertThresholdRamPct) {
                conditions.add(
                        AlertCondition(
                                MonitorAlertType.HIGH_RAM,
                                if (ram > 95) MonitorAlertSeverity.CRITICAL
                                else MonitorAlertSeverity.WARNING,
                                "RAM al ${ram}% (umbral: ${monitor.alertThresholdRamPct}%)",
                                BigDecimal(ram),
                                BigDecimal(monitor.alertThresholdRamPct)
                        )
                )
            }
        }

        // 6. High disk
        if (check.diskPercent != null) {
            val disk = check.diskPercent!!.toDouble()
            if (disk > monitor.alertThresholdDiskPct) {
                conditions.add(
                        AlertCondition(
                                MonitorAlertType.HIGH_DISK,
                                if (disk > 95) MonitorAlertSeverity.CRITICAL
                                else MonitorAlertSeverity.WARNING,
                                "Disco al ${disk}% (umbral: ${monitor.alertThresholdDiskPct}%)",
                                BigDecimal(disk),
                                BigDecimal(monitor.alertThresholdDiskPct)
                        )
                )
            }
        }

        // 7. SSL expiring
        if (check.sslDaysUntilExpiry != null) {
            val days = check.sslDaysUntilExpiry!!
            if (days < 0) {
                conditions.add(
                        AlertCondition(
                                MonitorAlertType.SSL_INVALID,
                                MonitorAlertSeverity.CRITICAL,
                                "Certificado SSL vencido",
                                BigDecimal(days),
                                BigDecimal.ZERO
                        )
                )
            } else if (days < 30) {
                conditions.add(
                        AlertCondition(
                                MonitorAlertType.SSL_EXPIRY,
                                if (days < 7) MonitorAlertSeverity.CRITICAL
                                else MonitorAlertSeverity.WARNING,
                                "Certificado SSL vence en $days días",
                                BigDecimal(days),
                                BigDecimal(30)
                        )
                )
            }
        }

        // Process each condition
        for (cond in conditions) {
            createAlertIfNew(monitor, check, cond)
        }

        // Auto-resolve DOWN/DEGRADED alerts when server comes back UP
        if (check.status == MonitorStatus.UP) {
            resolveActiveAlerts(monitor, check)
        }
    }

    private suspend fun createAlertIfNew(
            monitor: ServerMonitor,
            check: ServerCheck,
            condition: AlertCondition
    ) {
        val existing =
                monitorAlertRepo.findByMonitorIdAndIsResolvedOrderByTriggeredAtDesc(
                                monitor.id,
                                false
                        )
                        .firstOrNull { it.type == condition.type }

        if (existing != null) return // don't duplicate

        val alert =
                monitorAlertRepo.save(
                        MonitorAlert(
                                monitorId = monitor.id,
                                checkId = check.id,
                                type = condition.type,
                                severity = condition.severity,
                                message = condition.message,
                                value = condition.value,
                                threshold = condition.threshold,
                        )
                )

        sendNotifications(
                monitor,
                TelegramNotifyService.AlertPayload(
                        type = alert.type,
                        severity = alert.severity,
                        message = alert.message
                )
        )

        alert.notifiedEmail = monitor.notifyEmail != null
        alert.notifiedTelegram = monitor.notifyTelegramChatId != null
        monitorAlertRepo.save(alert)
    }

    private suspend fun resolveActiveAlerts(monitor: ServerMonitor, check: ServerCheck) {
        val activeAlerts =
                monitorAlertRepo.findByMonitorIdAndIsResolvedOrderByTriggeredAtDesc(
                                monitor.id,
                                false
                        )
                        .filter {
                            it.type == MonitorAlertType.DOWN || it.type == MonitorAlertType.DEGRADED
                        }

        if (activeAlerts.isEmpty()) return

        activeAlerts.forEach { alert ->
            alert.isResolved = true
            alert.resolvedAt = Instant.now()
            monitorAlertRepo.save(alert)
        }

        sendNotifications(
                monitor,
                TelegramNotifyService.AlertPayload(
                        type = MonitorAlertType.RECOVERY,
                        severity = MonitorAlertSeverity.OK,
                        message = "✅ Servidor recuperado — volvió a responder correctamente"
                )
        )
    }

    private suspend fun sendNotifications(
            monitor: ServerMonitor,
            alert: TelegramNotifyService.AlertPayload,
    ) {
        val hostname = "unknown"
        val ip = "N/A"

        if (monitor.notifyTelegramChatId != null) {
            telegram.sendAlert(monitor, hostname, ip, alert, monitor.notifyTelegramChatId!!)
        }

        if (monitor.notifyEmail != null) {
            val isRecovery = alert.type == MonitorAlertType.RECOVERY
            val subject =
                    if (isRecovery) "✅ [RECOVERY] $hostname — volvió a línea"
                    else "🔴 [${alert.severity}] $hostname — ${alert.type.name.lowercase()}"

            try {
                notifications.send(
                        monitor.notifyEmail!!,
                        subject,
                        buildEmailHtml(hostname, ip, alert)
                )
            } catch (e: Exception) {
                log.warn("Email send failed: ${e.message}")
            }
        }
    }

    private fun buildEmailHtml(
            hostname: String,
            ip: String,
            alert: TelegramNotifyService.AlertPayload
    ): String {
        val isRecovery = alert.type == MonitorAlertType.RECOVERY
        val color = if (isRecovery) "#10b981" else "#ef4444"
        val formatter =
                java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
                        .withZone(java.time.ZoneId.of("UTC"))
        val time = formatter.format(Instant.now())

        return """
            <div style="font-family:monospace;max-width:600px;margin:0 auto;padding:20px;">
                <h2 style="color:$color">${if (isRecovery) "✅ Servidor recuperado" else "🔴 Alerta de servidor"}</h2>
                <table style="width:100%;border-collapse:collapse;">
                    <tr><td style="padding:8px;color:#666">Servidor</td><td style="padding:8px"><strong>$hostname</strong></td></tr>
                    <tr><td style="padding:8px;color:#666">IP</td><td style="padding:8px"><code>$ip</code></td></tr>
                    <tr><td style="padding:8px;color:#666">Tipo</td><td style="padding:8px">${alert.type}</td></tr>
                    <tr><td style="padding:8px;color:#666">Mensaje</td><td style="padding:8px">${alert.message}</td></tr>
                    <tr><td style="padding:8px;color:#666">Hora</td><td style="padding:8px">$time UTC</td></tr>
                </table>
            </div>
        """.trimIndent()
    }
}
