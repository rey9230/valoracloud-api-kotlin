package com.valoracloud.api.monitoring.service

import com.valoracloud.api.common.model.MonitorAlert
import com.valoracloud.api.common.model.MonitorAlertSeverity
import com.valoracloud.api.common.model.MonitorAlertType
import com.valoracloud.api.entity.ServerMonitor
import org.slf4j.LoggerFactory
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Service
class TelegramNotifyService(
    @Value("\${TELEGRAM_BOT_TOKEN:}") private val botToken: String,
) {
    private val log = LoggerFactory.getLogger(TelegramNotifyService::class.java)
    private val httpClient = HttpClient.newHttpClient()

    suspend fun sendAlert(
        monitor: ServerMonitor,
        hostname: String,
        ipAddress: String?,
        alert: AlertPayload,
        chatId: String,
    ) {
        if (botToken.isBlank()) return

        val emoji = when (alert.severity) {
            MonitorAlertSeverity.CRITICAL -> "\uD83D\uDD34"
            MonitorAlertSeverity.WARNING -> "\uD83D\uDFE1"
            MonitorAlertSeverity.INFO -> "\uD83D\uDD35"
            MonitorAlertSeverity.OK -> "\uD83D\uDFE2"
        }

        val ip = ipAddress ?: "N/A"
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
            .withZone(ZoneId.of("UTC"))
        val time = formatter.format(Instant.now())

        val text = listOf(
            "$emoji *VALORA CLOUD MONITOR*",
            "",
            "*Servidor:* `$hostname`",
            "*IP:* `$ip`",
            "",
            "*Alerta:* ${alert.message}",
            "*Severidad:* ${alert.severity}",
            "*Hora:* $time UTC",
        ).joinToString("\n")

        try {
            val body = jacksonObjectMapper().writeValueAsString(
                mapOf(
                    "chat_id" to chatId,
                    "text" to text,
                    "parse_mode" to "Markdown",
                )
            )

            val request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.telegram.org/bot$botToken/sendMessage"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()

            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: Exception) {
            log.warn("Telegram notification error: ${e.message}")
        }
    }

    data class AlertPayload(
        val type: MonitorAlertType,
        val severity: MonitorAlertSeverity,
        val message: String,
    )
}
