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
    private val templateRenderer: HandlebarsTemplateRenderer,
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
        val preheader = locale["welcome_preheader"] ?: "Verify your email to get started."
        val headerLabel = locale["welcome_eyebrow"] ?: "ACCOUNT · VERIFY"

        val html = templateRenderer.render(
            templateName = "welcome",
            data = mapOf(
                "verifyUrl" to verifyUrl,
                "token" to token,
            ),
            language = language,
            subject = subject,
            preheader = preheader,
            headerLabel = headerLabel,
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
        val preheader = locale["password_reset_preheader"] ?: "Reset your password."
        val headerLabel = locale["password_reset_eyebrow"] ?: "SECURITY · RESET"

        val html = templateRenderer.render(
            templateName = "password-reset",
            data = mapOf(
                "resetUrl" to resetUrl,
                "token" to token,
                "to" to email,
                "requestedAt" to requestedAt,
                "fromIp" to fromIp,
                "location" to location,
                "device" to device,
            ),
            language = language,
            subject = subject,
            preheader = preheader,
            headerLabel = headerLabel,
        )
        sendEmail(email, subject, html, "password-reset", language)
    }

    fun sendPaymentConfirmationEmail(email: String, orderId: String, amount: Double,
                                     language: String = "en", userId: String? = null) {
        val locale = loadLocale(language)
        val subject = "${locale["payment_confirmed_subject"] ?: "Payment Confirmed"} – $brandName"
        val preheader = locale["payment_confirmed_preheader"] ?: "Your payment has been received."
        val headerLabel = locale["payment_confirmed_eyebrow"] ?: "BILLING · RECEIPT"

        val html = templateRenderer.render(
            templateName = "payment-confirmed",
            data = mapOf(
                "orderId" to orderId,
                "amount" to String.format("%.2f", amount),
            ),
            language = language,
            subject = subject,
            preheader = preheader,
            headerLabel = headerLabel,
        )
        sendEmail(email, subject, html, "payment-confirmed", language, userId)
    }

    fun sendServerReadyEmail(email: String, ipAddress: String, username: String, hostname: String,
                             language: String = "en", userId: String? = null) {
        val locale = loadLocale(language)
        val subject = "${locale["server_ready_subject"] ?: "Your Server is Ready"} – $brandName"
        val preheader = locale["server_ready_preheader"] ?: "Your server is live and ready."
        val headerLabel = locale["server_ready_eyebrow"] ?: "VPS · PROVISIONED"

        val html = templateRenderer.render(
            templateName = "server-ready",
            data = mapOf(
                "ipAddress" to ipAddress,
                "username" to username,
                "hostname" to hostname,
            ),
            language = language,
            subject = subject,
            preheader = preheader,
            headerLabel = headerLabel,
        )
        sendEmail(email, subject, html, "server-provisioned", language, userId)
    }

    fun sendExpirationWarningEmail(email: String, hostname: String, expiresAt: String,
                                   language: String = "en", userId: String? = null) {
        val locale = loadLocale(language)
        val subject = "${locale["server_expiring_subject"] ?: "Server Expiring Soon"} – $brandName"
        val preheader = locale["server_expiring_preheader"] ?: "Your server is expiring soon."
        val headerLabel = locale["server_expiring_eyebrow"] ?: "VPS · EXPIRING"

        val html = templateRenderer.render(
            templateName = "server-expiring",
            data = mapOf(
                "hostname" to hostname,
                "expiresAt" to expiresAt,
            ),
            language = language,
            subject = subject,
            preheader = preheader,
            headerLabel = headerLabel,
        )
        sendEmail(email, subject, html, "server-expiration-warning", language, userId)
    }

    fun sendServiceSuspendedEmail(email: String, hostname: String, deleteDate: String,
                                  language: String = "en", userId: String? = null) {
        val locale = loadLocale(language)
        val subject = "${locale["service_suspended_subject"] ?: "Service Suspended"} – $brandName"
        val preheader = locale["service_suspended_preheader"] ?: "Your service has been suspended."
        val headerLabel = locale["service_suspended_eyebrow"] ?: "SERVICE · SUSPENDED"

        val html = templateRenderer.render(
            templateName = "service-suspended",
            data = mapOf(
                "hostname" to hostname,
                "deleteDate" to deleteDate,
            ),
            language = language,
            subject = subject,
            preheader = preheader,
            headerLabel = headerLabel,
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
        val preheader = locale["maintenance_preheader"] ?: "Scheduled maintenance is coming up."
        val headerLabel = locale["maintenance_eyebrow"] ?: "NOTICE · SCHEDULED"

        val html = templateRenderer.render(
            templateName = "scheduled-maintenance",
            data = mapOf(
                "region" to region,
                "window" to window,
                "localTime" to localTime,
                "duration" to duration,
                "impact" to impact,
                "reference" to reference,
                "hostname" to hostname,
            ),
            language = language,
            subject = subject,
            preheader = preheader,
            headerLabel = headerLabel,
        )
        sendEmail(email, subject, html, "scheduled-maintenance", language, userId)
    }

    fun sendIncidentEmail(
        email: String, region: String, startedAt: String, services: String,
        reference: String, language: String = "en", userId: String? = null,
    ) {
        val locale = loadLocale(language)
        val subject = "${locale["incident_subject"] ?: "Incident Notification"} – $brandName"
        val preheader = locale["incident_preheader"] ?: "We are investigating an incident."
        val headerLabel = locale["incident_eyebrow"] ?: "INCIDENT · INVESTIGATING"

        val html = templateRenderer.render(
            templateName = "incident",
            data = mapOf(
                "region" to region,
                "startedAt" to startedAt,
                "services" to services,
                "reference" to reference,
            ),
            language = language,
            subject = subject,
            preheader = preheader,
            headerLabel = headerLabel,
        )
        sendEmail(email, subject, html, "incident", language, userId)
    }

    fun sendDomainRegisteredEmail(email: String, domainName: String, expiresAt: String,
                                  language: String = "en", userId: String? = null) {
        val locale = loadLocale(language)
        val subject = "${locale["domain_registered_subject"] ?: "Domain Registered"} – $brandName"
        val preheader = locale["domain_registered_preheader"] ?: "Your domain has been registered."
        val headerLabel = locale["domain_registered_eyebrow"] ?: "DOMAINS · REGISTERED"

        val html = templateRenderer.render(
            templateName = "domain-registered",
            data = mapOf(
                "domainName" to domainName,
                "expiresAt" to expiresAt,
            ),
            language = language,
            subject = subject,
            preheader = preheader,
            headerLabel = headerLabel,
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
        val preheader = locale["welcome_preheader"] ?: "Verify your email to get started."
        val headerLabel = locale["welcome_eyebrow"] ?: "ACCOUNT · WELCOME"

        val html = templateRenderer.render(
            templateName = "welcome",
            data = mapOf(
                "verifyUrl" to verifyUrl,
                "token" to token,
            ),
            language = language,
            subject = subject,
            preheader = preheader,
            headerLabel = headerLabel,
        )
        sendEmail(email, subject, html, "welcome", language, userId)
    }

    fun sendNewLoginEmail(
        email: String, signedInAt: String, fromIp: String, location: String,
        device: String, authMethod: String, language: String = "en", userId: String? = null,
    ) {
        val locale = loadLocale(language)
        val subject = "${locale["new_login_subject"] ?: "New Login Detected"} – $brandName"
        val preheader = locale["new_login_preheader"] ?: "A new login was detected on your account."
        val headerLabel = locale["new_login_eyebrow"] ?: "SECURITY · NEW DEVICE"

        val html = templateRenderer.render(
            templateName = "new-login",
            data = mapOf(
                "to" to email,
                "signedInAt" to signedInAt,
                "fromIp" to fromIp,
                "location" to location,
                "device" to device,
                "authMethod" to authMethod,
            ),
            language = language,
            subject = subject,
            preheader = preheader,
            headerLabel = headerLabel,
        )
        sendEmail(email, subject, html, "new-login", language, userId)
    }

    fun sendPasswordChangedEmail(
        email: String, changedAt: String, fromIp: String, location: String,
        device: String, language: String = "en", userId: String? = null,
    ) {
        val locale = loadLocale(language)
        val subject = "${locale["password_changed_subject"] ?: "Your Password Was Changed"} – $brandName"
        val preheader = locale["password_changed_preheader"] ?: "Your account password was changed."
        val headerLabel = locale["password_changed_eyebrow"] ?: "SECURITY · CONFIRMATION"

        val html = templateRenderer.render(
            templateName = "password-changed",
            data = mapOf(
                "to" to email,
                "changedAt" to changedAt,
                "fromIp" to fromIp,
                "location" to location,
                "device" to device,
            ),
            language = language,
            subject = subject,
            preheader = preheader,
            headerLabel = headerLabel,
        )
        sendEmail(email, subject, html, "password-changed", language, userId)
    }


    fun sendObjectStorageReadyEmail(
        email: String, displayName: String, s3Endpoint: String, accessKey: String,
        secretKey: String, region: String, language: String = "en", userId: String? = null,
    ) {
        val locale = loadLocale(language)
        val subject = "${locale["object_storage_ready_subject"] ?: "Your Object Storage is Ready"} – $brandName"
        val preheader = locale["object_storage_ready_preheader"] ?: "Your object storage bucket is ready."
        val headerLabel = locale["object_storage_ready_eyebrow"] ?: "STORAGE · PROVISIONED"

        val html = templateRenderer.render(
            templateName = "object-storage-ready",
            data = mapOf(
                "displayName" to displayName,
                "s3Endpoint" to s3Endpoint,
                "accessKey" to accessKey,
                "secretKey" to secretKey,
                "region" to region,
            ),
            language = language,
            subject = subject,
            preheader = preheader,
            headerLabel = headerLabel,
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
        val preheader = locale["backup_failed_preheader"] ?: "A scheduled backup failed."
        val headerLabel = locale["backup_failed_eyebrow"] ?: "BACKUP · FAILED"

        val html = templateRenderer.render(
            templateName = "backup-failed",
            data = mapOf(
                "hostname" to hostname,
                "backupId" to backupId,
                "scheduledAt" to scheduledAt,
                "failedAt" to failedAt,
                "reason" to reason,
                "lastSuccessful" to lastSuccessful,
            ),
            language = language,
            subject = subject,
            preheader = preheader,
            headerLabel = headerLabel,
        )
        sendEmail(email, subject, html, "backup-failed", language, userId)
    }

    fun sendReinstallCompleteEmail(
        email: String, hostname: String, newPassword: String,
        language: String = "en", userId: String? = null,
    ) {
        val locale = loadLocale(language)
        val subject = "${locale["reinstall_complete_subject"] ?: "Reinstallation Complete"} – $brandName"
        val preheader = locale["reinstall_complete_preheader"] ?: "Your server has been reinstalled."
        val headerLabel = locale["reinstall_complete_eyebrow"] ?: "VPS · REINSTALLED"

        val html = templateRenderer.render(
            templateName = "reinstall-complete",
            data = mapOf(
                "hostname" to hostname,
                "newPassword" to newPassword,
            ),
            language = language,
            subject = subject,
            preheader = preheader,
            headerLabel = headerLabel,
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
        val preheader = locale["ticket_preheader"] ?: "Your support request has been received."
        val headerLabel = locale["ticket_eyebrow"] ?: "SUPPORT · TICKET"

        val html = templateRenderer.render(
            templateName = "support-ticket",
            data = mapOf(
                "firstName" to firstName,
                "ticketId" to ticketId,
                "ticketSubject" to ticketSubject,
                "priority" to priority,
                "openedAt" to openedAt,
                "firstReplyExpected" to firstReplyExpected,
                "message" to message,
            ),
            language = language,
            subject = subject,
            preheader = preheader,
            headerLabel = headerLabel,
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

    fun sendCancellationFailureAlert(
        serviceType: String,
        resourceId: String,
        contaboId: String,
        userId: String,
        errorMessage: String,
        errorStack: String?,
    ) {
        val now = Instant.now().toString()
        val html = """
<!DOCTYPE html>
<html>
<head><meta charset="utf-8"/></head>
<body style="font-family:monospace;background:#0d1117;color:#c9d1d9;padding:24px">
  <h2 style="color:#f85149;margin:0 0 16px">🚨 Cancellation Failed — Manual Action Required</h2>
  <p style="color:#f0883e;margin:0 0 16px">The following resource could <b>NOT</b> be cancelled in Contabo and is still running. Manual cancellation is required to avoid charges.</p>
  <table style="border-collapse:collapse;width:100%">
    <tr><td style="padding:4px 12px 4px 0;color:#8b949e">Service Type</td><td><b>${serviceType.uppercase()}</b></td></tr>
    <tr><td style="padding:4px 12px 4px 0;color:#8b949e">Resource ID (DB)</td><td><b>$resourceId</b></td></tr>
    <tr><td style="padding:4px 12px 4px 0;color:#8b949e">Contabo ID</td><td><b>$contaboId</b></td></tr>
    <tr><td style="padding:4px 12px 4px 0;color:#8b949e">User ID</td><td>$userId</td></tr>
    <tr><td style="padding:4px 12px 4px 0;color:#8b949e">Timestamp</td><td>$now</td></tr>
  </table>
  <h3 style="color:#f0883e;margin:20px 0 8px">Error</h3>
  <pre style="background:#161b22;border:1px solid #30363d;padding:12px;border-radius:6px;overflow:auto;white-space:pre-wrap">$errorMessage</pre>
  ${if (errorStack != null) "<h3 style=\"color:#8b949e;margin:16px 0 8px\">Stack Trace</h3><pre style=\"background:#161b22;border:1px solid #30363d;padding:12px;border-radius:6px;overflow:auto;white-space:pre-wrap;font-size:11px\">$errorStack</pre>" else ""}
  <p style="margin-top:24px;color:#f85149"><b>Action required:</b> Cancel this resource manually in the Contabo panel to avoid continued billing.</p>
</body>
</html>
        """.trimIndent()

        try {
            val msg = mailSender.createMimeMessage()
            val helper = MimeMessageHelper(msg, true)
            helper.setFrom("\"$brandName\" <$from>")
            helper.setTo("renielgonzalez@valoracloud.com")
            helper.setSubject("🚨 [CANCELLATION FAILED] ${serviceType.uppercase()} — $contaboId")
            helper.setText(html, true)
            mailSender.send(msg)
            log.info("Cancellation failure alert sent for $serviceType/$resourceId (contaboId=$contaboId)")
        } catch (e: Exception) {
            log.error("Failed to send cancellation failure alert for $serviceType/$resourceId: ${e.message}")
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

    private fun interpolate(str: String, vars: Map<String, String>): String {
        val regex = Regex("\\{\\{(\\w+)\\}\\}")
        return regex.replace(str) { vars[it.groupValues[1]] ?: "" }
    }

    companion object {
        // Basic fallback locales for backward compatibility
        private val LOCALE_EN = mapOf(
            "welcome_subject" to "Welcome to {{brandName}}",
            "welcome_preheader" to "Verify your email to get started.",
            "password_reset_subject" to "Password Reset",
            "password_reset_preheader" to "Reset your password.",
            "payment_confirmed_subject" to "Payment Confirmed",
            "payment_confirmed_preheader" to "Your payment has been received.",
            "server_ready_subject" to "Your Server is Ready",
            "server_ready_preheader" to "Your server is live and ready.",
            "server_expiring_subject" to "Server Expiring Soon",
            "server_expiring_preheader" to "Your server is expiring soon.",
            "service_suspended_subject" to "Service Suspended",
            "service_suspended_preheader" to "Your service has been suspended.",
            "maintenance_subject" to "Scheduled Maintenance",
            "maintenance_preheader" to "Scheduled maintenance is coming up.",
            "incident_subject" to "Incident Notification",
            "incident_preheader" to "We are investigating an incident.",
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
            "password_reset_subject" to "Restablecer Contraseña",
            "password_reset_preheader" to "Restablece tu contraseña.",
            "payment_confirmed_subject" to "Pago Confirmado",
            "payment_confirmed_preheader" to "Tu pago ha sido recibido.",
            "server_ready_subject" to "Tu Servidor Está Listo",
            "server_ready_preheader" to "Tu servidor está activo y listo.",
            "server_expiring_subject" to "Servidor Por Vencer",
            "server_expiring_preheader" to "Tu servidor está por vencer.",
            "service_suspended_subject" to "Servicio Suspendido",
            "service_suspended_preheader" to "Tu servicio ha sido suspendido.",
            "maintenance_subject" to "Mantenimiento Programado",
            "maintenance_preheader" to "Mantenimiento programado para tu región.",
            "incident_subject" to "Notificación de Incidente",
            "incident_preheader" to "Estamos investigando un incidente.",
            "domain_registered_subject" to "Dominio Registrado",
            "domain_registered_preheader" to "Tu dominio ha sido registrado exitosamente.",
            "new_login_subject" to "Nuevo Inicio de Sesión Detectado",
            "new_login_preheader" to "Se detectó un nuevo inicio de sesión en tu cuenta.",
            "password_changed_subject" to "Tu Contraseña Ha Sido Cambiada",
            "password_changed_preheader" to "La contraseña de tu cuenta ha sido cambiada.",
            "object_storage_ready_subject" to "Tu Almacenamiento de Objetos Está Listo",
            "object_storage_ready_preheader" to "Tu bucket de almacenamiento de objetos está listo.",
            "backup_failed_subject" to "Copia de Seguridad Fallida",
            "backup_failed_preheader" to "Una copia de seguridad programada ha fallada.",
            "reinstall_complete_subject" to "Reinstalación Completada",
            "reinstall_complete_preheader" to "Tu servidor ha sido reinstalado.",
            "ticket_subject" to "Ticket de Soporte Recibido",
            "ticket_preheader" to "Tu solicitud de soporte ha sido recibida.",
        )
    }
}
