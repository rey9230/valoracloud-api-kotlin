package com.valoracloud.api.notifications.service

import com.valoracloud.api.config.EmailLogRepository
import com.valoracloud.api.entity.EmailLog
import jakarta.mail.internet.MimeMessage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class NotificationsService(
    private val mailSender: JavaMailSender,
    private val emailLogRepo: EmailLogRepository,
    @Value("\${SMTP_FROM:support@valoracloud.com}") private val from: String,
    @Value("\${BRAND_NAME:Valora Cloud}") private val brandName: String,
    @Value("\${BRAND_DOMAIN:valoracloud.com}") private val brandDomain: String,
    @Value("\${FRONTEND_URL:http://localhost:5173}") private val frontendUrl: String,
) {
    private val log = LoggerFactory.getLogger(NotificationsService::class.java)

    fun send(to: String, subject: String, html: String) {
        sendEmail(to, subject, html, "general")
    }

    // ─── Public methods ─────────────────────────────────────────────────

    fun sendVerificationEmail(email: String, token: String, language: String = "en") {
        val verifyUrl = "$frontendUrl/verify-email?token=$token"
        val locale = loadLocale(language)
        val subject = interpolate(locale["welcome_subject"] ?: "Welcome to $brandName", mapOf("brandName" to brandName))
        val html = buildHtmlTemplate(
            headerLabel = "ACCOUNT · VERIFY",
            preheader = locale["welcome_preheader"] ?: "Verify your email to get started.",
            bodyLines = listOf(
                "<p style=\"font-size:16px;line-height:1.6\">${locale["welcome_body"] ?: "Click the button below to verify your email address."}</p>",
                "<a href=\"$verifyUrl\" style=\"display:inline-block;padding:12px 24px;background:#6366f1;color:#fff;text-decoration:none;border-radius:6px;font-weight:600\">Verify Email</a>",
            ),
        )
        sendEmail(email, subject, html, "email-verification", language, null)
    }

    fun sendPasswordResetEmail(
        email: String, token: String, language: String = "en",
        requestedAt: String = "", fromIp: String = "", location: String = "", device: String = "",
    ) {
        val resetUrl = "$frontendUrl/reset-password?token=$token"
        val locale = loadLocale(language)
        val subject = "${locale["password_reset_subject"] ?: "Password Reset"} – $brandName"
        val html = buildHtmlTemplate(
            headerLabel = "SECURITY · RESET",
            preheader = locale["password_reset_preheader"] ?: "Reset your password.",
            bodyLines = listOf(
                "<p style=\"font-size:16px;line-height:1.6\">${locale["password_reset_body"] ?: "Click the button to reset your password."}</p>",
                "<a href=\"$resetUrl\" style=\"display:inline-block;padding:12px 24px;background:#6366f1;color:#fff;text-decoration:none;border-radius:6px;font-weight:600\">Reset Password</a>",
                securityDetails(requestedAt, fromIp, location, device),
            ),
        )
        sendEmail(email, subject, html, "password-reset", language)
    }

    fun sendPaymentConfirmationEmail(email: String, orderId: String, amount: Double,
                                     language: String = "en", userId: String? = null) {
        val locale = loadLocale(language)
        val subject = "${locale["payment_confirmed_subject"] ?: "Payment Confirmed"} – $brandName"
        val html = buildHtmlTemplate(
            headerLabel = "BILLING · RECEIPT",
            preheader = locale["payment_confirmed_preheader"] ?: "Your payment has been received.",
            bodyLines = listOf(
                "<p style=\"font-size:16px;line-height:1.6\">Payment received for order <strong>$orderId</strong></p>",
                "<p style=\"font-size:24px;font-weight:700;color:#10b981\">€${"%.2f".format(amount)}</p>",
            ),
        )
        sendEmail(email, subject, html, "payment-confirmed", language, userId)
    }

    fun sendServerReadyEmail(email: String, ipAddress: String, username: String, hostname: String,
                             language: String = "en", userId: String? = null) {
        val locale = loadLocale(language)
        val subject = "${locale["server_ready_subject"] ?: "Your Server is Ready"} – $brandName"
        val html = buildHtmlTemplate(
            headerLabel = "VPS · PROVISIONED",
            preheader = locale["server_ready_preheader"] ?: "Your server is live and ready.",
            bodyLines = listOf(
                detailRow("IP Address", "<code>$ipAddress</code>"),
                detailRow("Username", username),
                detailRow("Hostname", hostname),
            ),
        )
        sendEmail(email, subject, html, "server-provisioned", language, userId)
    }

    fun sendExpirationWarningEmail(email: String, hostname: String, expiresAt: String,
                                   language: String = "en", userId: String? = null) {
        val locale = loadLocale(language)
        val subject = "${locale["server_expiring_subject"] ?: "Server Expiring Soon"} – $brandName"
        val html = buildHtmlTemplate(
            headerLabel = "VPS · EXPIRING",
            preheader = locale["server_expiring_preheader"] ?: "Your server is expiring soon.",
            bodyLines = listOf(
                "<p style=\"font-size:16px;line-height:1.6\">Server <strong>$hostname</strong> expires on <strong>$expiresAt</strong></p>",
            ),
        )
        sendEmail(email, subject, html, "server-expiration-warning", language, userId)
    }

    fun sendServiceSuspendedEmail(email: String, hostname: String, deleteDate: String,
                                  language: String = "en", userId: String? = null) {
        val locale = loadLocale(language)
        val subject = "${locale["service_suspended_subject"] ?: "Service Suspended"} – $brandName"
        val html = buildHtmlTemplate(
            headerLabel = "SERVICE · SUSPENDED",
            preheader = locale["service_suspended_preheader"] ?: "Your service has been suspended.",
            bodyLines = listOf(
                "<p style=\"font-size:16px;line-height:1.6\">Server <strong>$hostname</strong> has been suspended. Data will be deleted on <strong>$deleteDate</strong>.</p>",
            ),
        )
        sendEmail(email, subject, html, "service-suspended", language, userId)
    }

    fun sendScheduledMaintenanceEmail(
        email: String, region: String, window: String, localTime: String,
        duration: String, impact: String, reference: String, hostname: String,
        language: String = "en", userId: String? = null,
    ) {
        val locale = loadLocale(language)
        val subject = "${locale["maintenance_subject"] ?: "Scheduled Maintenance"} – $brandName"
        val html = buildHtmlTemplate(
            headerLabel = "NOTICE · SCHEDULED",
            preheader = locale["maintenance_preheader"] ?: "Scheduled maintenance is coming up.",
            bodyLines = listOf(
                "<p style=\"font-size:16px;line-height:1.6\">Scheduled maintenance in region <strong>$region</strong></p>",
                detailRow("Window", window),
                detailRow("Local Time", localTime),
                detailRow("Duration", duration),
                detailRow("Impact", impact),
                detailRow("Reference", reference),
                if (hostname.isNotBlank()) detailRow("Server", hostname) else "",
            ),
        )
        sendEmail(email, subject, html, "scheduled-maintenance", language, userId)
    }

    fun sendIncidentEmail(
        email: String, region: String, startedAt: String, services: String,
        reference: String, language: String = "en", userId: String? = null,
    ) {
        val locale = loadLocale(language)
        val subject = "${locale["incident_subject"] ?: "Incident Notification"} – $brandName"
        val html = buildHtmlTemplate(
            headerLabel = "INCIDENT · INVESTIGATING",
            preheader = locale["incident_preheader"] ?: "We are investigating an incident.",
            bodyLines = listOf(
                "<p style=\"font-size:16px;line-height:1.6\">Incident in region <strong>$region</strong></p>",
                detailRow("Started At", startedAt),
                detailRow("Services", services),
                detailRow("Reference", reference),
            ),
        )
        sendEmail(email, subject, html, "incident", language, userId)
    }

    fun sendDomainRegisteredEmail(email: String, domainName: String, expiresAt: String,
                                  language: String = "en", userId: String? = null) {
        val locale = loadLocale(language)
        val subject = "${locale["domain_registered_subject"] ?: "Domain Registered"} – $brandName"
        val html = buildHtmlTemplate(
            headerLabel = "DOMAINS · REGISTERED",
            preheader = locale["domain_registered_preheader"] ?: "Your domain has been registered.",
            bodyLines = listOf(
                "<p style=\"font-size:16px;line-height:1.6\">Domain <strong>$domainName</strong> registered successfully</p>",
                detailRow("Expires At", expiresAt),
            ),
        )
        sendEmail(email, subject, html, "domain-registered", language, userId)
    }

    // ─── New methods ────────────────────────────────────────────────────

    fun sendWelcomeEmail(
        email: String, token: String = "", language: String = "en", userId: String? = null,
    ) {
        val verifyUrl = if (token.isNotBlank()) "$frontendUrl/verify-email?token=$token" else ""
        val locale = loadLocale(language)
        val subject = interpolate(locale["welcome_subject"] ?: "Welcome to $brandName", mapOf("brandName" to brandName))
        val bodyLines = mutableListOf<String>()
        bodyLines.add("<p style=\"font-size:16px;line-height:1.6\">${locale["welcome_body"] ?: "Click the button below to verify your email address and get started."}</p>")
        if (verifyUrl.isNotBlank()) {
            bodyLines.add("<a href=\"$verifyUrl\" style=\"display:inline-block;padding:12px 24px;background:#6366f1;color:#fff;text-decoration:none;border-radius:6px;font-weight:600\">Verify Email</a>")
        }
        val html = buildHtmlTemplate(
            headerLabel = "ACCOUNT · WELCOME",
            preheader = locale["welcome_preheader"] ?: "Verify your email to get started.",
            bodyLines = bodyLines,
        )
        sendEmail(email, subject, html, "welcome", language, userId)
    }

    fun sendNewLoginEmail(
        email: String, signedInAt: String, fromIp: String, location: String,
        device: String, authMethod: String, language: String = "en", userId: String? = null,
    ) {
        val locale = loadLocale(language)
        val subject = "${locale["new_login_subject"] ?: "New Login Detected"} – $brandName"
        val html = buildHtmlTemplate(
            headerLabel = "SECURITY · NEW DEVICE",
            preheader = locale["new_login_preheader"] ?: "A new login was detected on your account.",
            bodyLines = listOf(
                detailRow("Time", signedInAt),
                detailRow("IP", fromIp),
                detailRow("Location", location),
                detailRow("Device", device),
                detailRow("Auth Method", authMethod),
            ),
        )
        sendEmail(email, subject, html, "new-login", language, userId)
    }

    fun sendPasswordChangedEmail(
        email: String, changedAt: String, fromIp: String, location: String,
        device: String, language: String = "en", userId: String? = null,
    ) {
        val locale = loadLocale(language)
        val subject = "${locale["password_changed_subject"] ?: "Your Password Was Changed"} – $brandName"
        val html = buildHtmlTemplate(
            headerLabel = "SECURITY · CONFIRMATION",
            preheader = locale["password_changed_preheader"] ?: "Your account password was changed.",
            bodyLines = listOf(
                detailRow("Time", changedAt),
                detailRow("IP", fromIp),
                detailRow("Location", location),
                detailRow("Device", device),
            ),
        )
        sendEmail(email, subject, html, "password-changed", language, userId)
    }

    fun sendObjectStorageReadyEmail(
        email: String, displayName: String, s3Endpoint: String, accessKey: String,
        secretKey: String, region: String, language: String = "en", userId: String? = null,
    ) {
        val locale = loadLocale(language)
        val subject = "${locale["object_storage_ready_subject"] ?: "Your Object Storage is Ready"} – $brandName"
        val html = buildHtmlTemplate(
            headerLabel = "STORAGE · PROVISIONED",
            preheader = locale["object_storage_ready_preheader"] ?: "Your object storage bucket is ready.",
            bodyLines = listOf(
                detailRow("Name", displayName),
                detailRow("Endpoint", "<code>$s3Endpoint</code>"),
                detailRow("Access Key", "<code>$accessKey</code>"),
                detailRow("Secret Key", "<code>$secretKey</code>"),
                detailRow("Region", region),
            ),
        )
        sendEmail(email, subject, html, "object-storage-provisioned", language, userId)
    }

    fun sendBackupFailedEmail(
        email: String, hostname: String, backupId: String, scheduledAt: String,
        failedAt: String, reason: String, lastSuccessful: String,
        language: String = "en", userId: String? = null,
    ) {
        val locale = loadLocale(language)
        val subject = "${locale["backup_failed_subject"] ?: "Backup Failed"} – $brandName"
        val html = buildHtmlTemplate(
            headerLabel = "BACKUP · FAILED",
            preheader = locale["backup_failed_preheader"] ?: "A scheduled backup failed.",
            bodyLines = listOf(
                detailRow("Server", hostname),
                detailRow("Backup ID", backupId),
                detailRow("Scheduled At", scheduledAt),
                detailRow("Failed At", failedAt),
                detailRow("Reason", reason),
                detailRow("Last Successful", lastSuccessful),
            ),
        )
        sendEmail(email, subject, html, "backup-failed", language, userId)
    }

    fun sendReinstallCompleteEmail(
        email: String, hostname: String, newPassword: String,
        language: String = "en", userId: String? = null,
    ) {
        val locale = loadLocale(language)
        val subject = "${locale["reinstall_complete_subject"] ?: "Reinstallation Complete"} – $brandName"
        val html = buildHtmlTemplate(
            headerLabel = "VPS · REINSTALLED",
            preheader = locale["reinstall_complete_preheader"] ?: "Your server has been reinstalled.",
            bodyLines = listOf(
                detailRow("Server", hostname),
                detailRow("New Password", "<code>$newPassword</code>"),
            ),
        )
        sendEmail(email, subject, html, "server-reinstalled", language, userId)
    }

    fun sendSupportTicketEmail(
        email: String, firstName: String, ticketId: String, ticketSubject: String,
        priority: String, openedAt: String, firstReplyExpected: String, message: String,
        language: String = "en", userId: String? = null,
    ) {
        val locale = loadLocale(language)
        val subject = "${locale["ticket_subject"] ?: "Support Ticket Received"} [$ticketId] – $brandName"
        val html = buildHtmlTemplate(
            headerLabel = "TICKET · $ticketId",
            preheader = locale["ticket_preheader"] ?: "Your support request has been received.",
            bodyLines = listOf(
                "<p style=\"font-size:16px;line-height:1.6\">Hi $firstName,</p>",
                detailRow("Ticket ID", ticketId),
                detailRow("Subject", ticketSubject),
                detailRow("Priority", priority),
                detailRow("Opened At", openedAt),
                detailRow("First Reply Expected", firstReplyExpected),
                "<p style=\"margin-top:16px;padding:16px;background:#f8f8f8;border-radius:8px;font-size:14px;line-height:1.6\">$message</p>",
            ),
        )
        sendEmail(email, subject, html, "support-ticket", language, userId)
    }

    // ─── Admin alert ────────────────────────────────────────────────────

    fun sendAdminProvisionAlert(
        context: String, serverId: String, hostname: String, ip: String,
        region: String, userId: String, errorMessage: String, errorStack: String?,
    ) {
        val adminEmail = "provision@valoracloud.com"
        val title = if (context == "reinstall")
            "⚠️ Reinstall Post-Provision Failed"
        else "⚠️ Provision Post-Provision Failed"
        val now = Instant.now().toString()
        val html = """
<!DOCTYPE html>
<html>
<head><meta charset="utf-8"/></head>
<body style="font-family:monospace;background:#0d1117;color:#c9d1d9;padding:24px">
  <h2 style="color:#f85149;margin:0 0 16px">$title</h2>
  <table style="border-collapse:collapse;width:100%">
    <tr><td style="padding:4px 12px 4px 0;color:#8b949e">Server ID</td><td><b>$serverId</b></td></tr>
    <tr><td style="padding:4px 12px 4px 0;color:#8b949e">Hostname</td><td>${hostname.ifBlank { "N/A" }}</td></tr>
    <tr><td style="padding:4px 12px 4px 0;color:#8b949e">IP Address</td><td>${ip.ifBlank { "N/A" }}</td></tr>
    <tr><td style="padding:4px 12px 4px 0;color:#8b949e">Region</td><td>${region.ifBlank { "N/A" }}</td></tr>
    <tr><td style="padding:4px 12px 4px 0;color:#8b949e">User ID</td><td>$userId</td></tr>
    <tr><td style="padding:4px 12px 4px 0;color:#8b949e">Context</td><td>${context.uppercase()}</td></tr>
    <tr><td style="padding:4px 12px 4px 0;color:#8b949e">Timestamp</td><td>$now</td></tr>
  </table>
  <h3 style="color:#f0883e;margin:20px 0 8px">Error</h3>
  <pre style="background:#161b22;border:1px solid #30363d;padding:12px;border-radius:6px;overflow:auto;white-space:pre-wrap">$errorMessage</pre>
  ${if (errorStack != null) "<h3 style=\"color:#8b949e;margin:16px 0 8px\">Stack Trace</h3><pre style=\"background:#161b22;border:1px solid #30363d;padding:12px;border-radius:6px;overflow:auto;white-space:pre-wrap;font-size:11px\">$errorStack</pre>" else ""}
  <p style="margin-top:24px;color:#8b949e">Server status has been set to <b style="color:#f85149">NEEDS_PROVISION</b>.</p>
  <p style="color:#8b949e">Fix via: <code style="color:#79c0ff">PATCH /admin/servers/$serverId/status</code></p>
</body>
</html>
        """.trimIndent()

        try {
            val msg = mailSender.createMimeMessage()
            val helper = MimeMessageHelper(msg, true)
            helper.setFrom(from)
            helper.setTo(adminEmail)
            helper.setSubject("[${context.uppercase()}] Post-provision failed — ${hostname.ifBlank { serverId }}")
            helper.setText(html, true)
            mailSender.send(msg)
            log.info("Admin provision alert sent -> $adminEmail [server:$serverId]")
        } catch (e: Exception) {
            log.error("Failed to send admin provision alert: ${e.message}")
        }
    }

    // ─── Core sendEmail ──────────────────────────────────────────────────

    private fun sendEmail(
        to: String, subject: String, html: String, triggeredBy: String,
        language: String = "en", userId: String? = null,
    ) {
        var status = "sent"
        var errorMsg: String? = null
        try {
            val msg = mailSender.createMimeMessage()
            val helper = MimeMessageHelper(msg, true)
            helper.setFrom("\"$brandName\" <$from>")
            helper.setTo(to)
            helper.setSubject(subject)
            helper.setText(html, true)
            mailSender.send(msg)
            log.info("Email sent -> $to [$triggeredBy] ($language)")
        } catch (e: Exception) {
            status = "failed"
            errorMsg = e.message
            log.error("Email failed -> $to [$triggeredBy]: $errorMsg")
        }

        try {
            emailLogRepo.save(EmailLog(
                userId = userId,
                to = to,
                templateName = triggeredBy,
                subject = subject,
                language = language,
                status = status,
                triggeredBy = triggeredBy,
                renderedHtml = html,
                errorMessage = errorMsg,
            ))
        } catch (e: Exception) {
            log.error("Failed to write email log: ${e.message}")
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private fun loadLocale(language: String): Map<String, String> {
        val locale = if (language == "es") LOCALE_ES else LOCALE_EN
        return locale
    }

    private fun buildHtmlTemplate(
        headerLabel: String,
        preheader: String,
        bodyLines: List<String>,
    ): String {
        val body = bodyLines.joinToString("\n")
        return """
<!DOCTYPE html>
<html>
<head><meta charset="utf-8"/><meta name="viewport" content="width=device-width,initial-scale=1"/></head>
<body style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;background:#f4f4f5;margin:0;padding:0">
<table width="100%" cellpadding="0" cellspacing="0" style="max-width:600px;margin:0 auto;padding:32px 16px">
  <tr><td style="background:#18181b;padding:32px;border-radius:12px 12px 0 0">
    <table width="100%"><tr>
      <td style="color:#a78bfa;font-size:11px;letter-spacing:2px;text-transform:uppercase">$headerLabel</td>
      <td align="right" style="color:#71717a;font-size:13px">$brandName</td>
    </tr></table>
    <h1 style="color:#fafafa;font-size:20px;margin:24px 0 0;line-height:1.4">$preheader</h1>
  </td></tr>
  <tr><td style="background:#ffffff;padding:32px;border:1px solid #e4e4e7;border-top:0;border-radius:0 0 12px 12px">
    $body
  </td></tr>
  <tr><td style="padding:24px 0;text-align:center;color:#a1a1aa;font-size:12px">
    © ${java.time.Year.now()} $brandName · <a href="$frontendUrl" style="color:#6366f1;text-decoration:none">$brandDomain</a>
  </td></tr>
</table>
<div style="display:none;max-height:0;overflow:hidden">$preheader</div>
</body>
</html>
        """.trimIndent()
    }

    private fun detailRow(label: String, value: String): String =
        """<p style="margin:4px 0;font-size:14px"><span style="color:#71717a">$label:</span> $value</p>"""

    private fun securityDetails(
        requestedAt: String, fromIp: String, location: String, device: String,
    ): String {
        if (requestedAt.isBlank() && fromIp.isBlank()) return ""
        return """
<div style="margin-top:24px;padding:16px;background:#f8f8f8;border-radius:8px">
  <p style="font-size:12px;color:#71717a;text-transform:uppercase;margin:0 0 12px">Session Details</p>
  ${if (requestedAt.isNotBlank()) detailRow("Time", requestedAt) else ""}
  ${if (fromIp.isNotBlank()) detailRow("IP", fromIp) else ""}
  ${if (location.isNotBlank()) detailRow("Location", location) else ""}
  ${if (device.isNotBlank()) detailRow("Device", device) else ""}
</div>
        """.trimIndent()
    }

    private fun interpolate(str: String, vars: Map<String, String>): String {
        val regex = Regex("\\{\\{(\\w+)\\}\\}")
        return regex.replace(str) { vars[it.groupValues[1]] ?: "" }
    }

    companion object {
        private val LOCALE_EN = mapOf(
            "welcome_subject" to "Welcome to {{brandName}}",
            "welcome_preheader" to "Verify your email to get started.",
            "welcome_body" to "Click the button below to verify your email address and get started.",
            "password_reset_subject" to "Password Reset",
            "password_reset_preheader" to "Reset your password.",
            "password_reset_body" to "Someone requested a password reset for your account. Click below to reset it.",
            "payment_confirmed_subject" to "Payment Confirmed",
            "payment_confirmed_preheader" to "Your payment has been received.",
            "server_ready_subject" to "Your Server is Ready",
            "server_ready_preheader" to "Your server is live and ready.",
            "server_expiring_subject" to "Server Expiring Soon",
            "server_expiring_preheader" to "Your server is expiring soon. Renew to keep it running.",
            "service_suspended_subject" to "Service Suspended",
            "service_suspended_preheader" to "Your service has been suspended.",
            "maintenance_subject" to "Scheduled Maintenance",
            "maintenance_preheader" to "Scheduled maintenance is coming up for your region.",
            "incident_subject" to "Incident Notification",
            "incident_preheader" to "We are investigating an incident affecting your region.",
            "domain_registered_subject" to "Domain Registered",
            "domain_registered_preheader" to "Your domain has been successfully registered.",
            "new_login_subject" to "New Login Detected",
            "new_login_preheader" to "A new login was detected on your account.",
            "password_changed_subject" to "Your Password Was Changed",
            "password_changed_preheader" to "Your account password was changed.",
            "object_storage_ready_subject" to "Your Object Storage is Ready",
            "object_storage_ready_preheader" to "Your object storage bucket is ready.",
            "backup_failed_subject" to "Backup Failed",
            "backup_failed_preheader" to "A scheduled backup failed.",
            "reinstall_complete_subject" to "Reinstallation Complete",
            "reinstall_complete_preheader" to "Your server has been reinstalled.",
            "ticket_subject" to "Support Ticket Received",
            "ticket_preheader" to "Your support request has been received.",
        )

        private val LOCALE_ES = mapOf(
            "welcome_subject" to "Bienvenido a {{brandName}}",
            "welcome_preheader" to "Verifica tu correo para comenzar.",
            "welcome_body" to "Haz clic en el botón para verificar tu dirección de correo y empezar.",
            "password_reset_subject" to "Restablecer Contraseña",
            "password_reset_preheader" to "Restablece tu contraseña.",
            "password_reset_body" to "Alguien solicitó restablecer la contraseña de tu cuenta. Haz clic para continuar.",
            "payment_confirmed_subject" to "Pago Confirmado",
            "payment_confirmed_preheader" to "Tu pago ha sido recibido.",
            "server_ready_subject" to "Tu Servidor Está Listo",
            "server_ready_preheader" to "Tu servidor está activo y listo.",
            "server_expiring_subject" to "Servidor Por Vencer",
            "server_expiring_preheader" to "Tu servidor está por vencer. Renueva para mantenerlo activo.",
            "service_suspended_subject" to "Servicio Suspendido",
            "service_suspended_preheader" to "Tu servicio ha sido suspendido.",
            "maintenance_subject" to "Mantenimiento Programado",
            "maintenance_preheader" to "Mantenimiento programado para tu región.",
            "incident_subject" to "Notificación de Incidente",
            "incident_preheader" to "Estamos investigando un incidente que afecta tu región.",
            "domain_registered_subject" to "Dominio Registrado",
            "domain_registered_preheader" to "Tu dominio ha sido registrado exitosamente.",
            "new_login_subject" to "Nuevo Inicio de Sesión Detectado",
            "new_login_preheader" to "Se detectó un nuevo inicio de sesión en tu cuenta.",
            "password_changed_subject" to "Tu Contraseña Ha Sido Cambiada",
            "password_changed_preheader" to "La contraseña de tu cuenta ha sido cambiada.",
            "object_storage_ready_subject" to "Tu Almacenamiento de Objetos Está Listo",
            "object_storage_ready_preheader" to "Tu bucket de almacenamiento de objetos está listo.",
            "backup_failed_subject" to "Copia de Seguridad Fallida",
            "backup_failed_preheader" to "Una copia de seguridad programada ha fallado.",
            "reinstall_complete_subject" to "Reinstalación Completada",
            "reinstall_complete_preheader" to "Tu servidor ha sido reinstalado.",
            "ticket_subject" to "Ticket de Soporte Recibido",
            "ticket_preheader" to "Tu solicitud de soporte ha sido recibida.",
        )
    }
}
