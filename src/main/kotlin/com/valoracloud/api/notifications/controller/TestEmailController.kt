package com.valoracloud.api.notifications.controller

import com.valoracloud.api.notifications.service.NotificationsService
import io.swagger.v3.oas.annotations.Hidden
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/test")
@Hidden
class TestEmailController(
    private val notificationsService: NotificationsService
) {

    /**
     * Test endpoint to verify SMTP configuration.
     * Only available in dev/test profiles.
     *
     * Usage:
     *   POST http://localhost:8080/api/test/email
     *   Body: { "to": "your-email@example.com", "subject": "Test", "message": "Hello!" }
     */
    @PostMapping("/email")
    fun sendTestEmail(@RequestBody request: TestEmailRequest): Map<String, Any> {
        return try {
            notificationsService.send(
                to = request.to,
                subject = request.subject ?: "Test Email from Valora Cloud",
                html = buildTestEmailHtml(request.message ?: "This is a test email from your Valora Cloud API.")
            )
            mapOf(
                "success" to true,
                "message" to "Test email sent successfully to ${request.to}",
                "timestamp" to java.time.Instant.now().toString()
            )
        } catch (e: Exception) {
            mapOf(
                "success" to false,
                "error" to (e.message ?: "Unknown error"),
                "details" to e.stackTraceToString().take(500),
                "timestamp" to java.time.Instant.now().toString()
            )
        }
    }

    private fun buildTestEmailHtml(message: String): String = """
        <!DOCTYPE html>
        <html>
        <head><meta charset="utf-8"/></head>
        <body style="font-family:system-ui,sans-serif;padding:40px;background:#f4f4f5">
            <div style="max-width:600px;margin:0 auto;background:white;padding:32px;border-radius:12px;box-shadow:0 4px 6px rgba(0,0,0,0.1)">
                <h1 style="color:#6366f1;margin:0 0 16px">🧪 Email Test</h1>
                <p style="color:#3f3f46;font-size:16px;line-height:1.6">$message</p>
                <div style="margin-top:32px;padding:16px;background:#f4f4f5;border-radius:8px">
                    <p style="margin:0;font-size:14px;color:#71717a"><strong>Sent at:</strong> ${java.time.Instant.now()}</p>
                    <p style="margin:8px 0 0;font-size:14px;color:#71717a"><strong>Server:</strong> Valora Cloud API</p>
                </div>
            </div>
        </body>
        </html>
    """.trimIndent()
}

data class TestEmailRequest(
    val to: String,
    val subject: String? = null,
    val message: String? = null
)

