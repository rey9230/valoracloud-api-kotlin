package com.valoracloud.api.billing.service

import com.valoracloud.api.common.model.ServerStatus
import com.valoracloud.api.config.ServerRepository
import com.valoracloud.api.config.UserRepository
import com.valoracloud.api.notifications.service.NotificationsService
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * Scheduled tasks for billing operations. Runs every day at 9 AM UTC — sends expiration warnings
 * for servers expiring within the next 7 days (avoids duplicates by only notifying when expiresAt
 * is between 6d23h and 7d from now).
 */
@Service
class BillingSchedulerService(
        private val serverRepo: ServerRepository,
        private val userRepo: UserRepository,
        private val notifications: NotificationsService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 9 * * *", zone = "UTC")
    fun sendExpirationWarnings() {
        val now = Instant.now()
        val in7Days = now.plus(7, ChronoUnit.DAYS)
        val in6Days = now.plus(6, ChronoUnit.DAYS)

        // Servers expiring within the 7-day window that are still active
        val servers =
                serverRepo.findAll().filter { server ->
                    server.expiresAt != null &&
                            server.expiresAt!!.isAfter(in6Days) &&
                            !server.expiresAt!!.isAfter(in7Days) &&
                            server.status !in setOf(ServerStatus.SUSPENDED)
                }

        var sent = 0
        for (server in servers) {
            try {
                val user = userRepo.findById(server.userId).orElse(null) ?: continue
                notifications.sendExpirationWarningEmail(
                        email = user.email,
                        hostname = server.hostname,
                        expiresAt = server.expiresAt!!.toString(),
                        language = user.language,
                        userId = server.userId,
                )
                sent++
            } catch (e: Exception) {
                log.error("Expiration warning failed for server ${server.id}: ${e.message}", e)
            }
        }

        log.info("Expiration warnings sent: $sent servers")
    }
}
