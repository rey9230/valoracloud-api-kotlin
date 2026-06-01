package com.valoracloud.api.monitoring.checkers

import com.valoracloud.api.entity.ServerCheck
import com.valoracloud.api.common.model.MonitorStatus
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.time.Instant

object IcmpChecker {
    private val log = LoggerFactory.getLogger(IcmpChecker::class.java)

    fun check(host: String, port: Int? = null): ServerCheck {
        val start = System.currentTimeMillis()
        val check = ServerCheck(checkedAt = Instant.now(), checkerNode = "primary")

        return try {
            val address = InetAddress.getByName(host)
            val reachable = address.isReachable(5000)
            val pingMs = (System.currentTimeMillis() - start).toInt()

            check.apply {
                status = if (reachable) MonitorStatus.UP else MonitorStatus.DOWN
                this.pingMs = pingMs
                if (!reachable) errorMessage = "Host unreachable"
            }
        } catch (e: Exception) {
            log.warn("ICMP check failed for {}: {}", host, e.message)
            check.apply {
                status = MonitorStatus.DOWN
                errorMessage = e.message
                checkDurationMs = (System.currentTimeMillis() - start).toInt()
            }
        }
    }
}
