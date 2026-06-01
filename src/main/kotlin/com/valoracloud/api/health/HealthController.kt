package com.valoracloud.api.health

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.sql.DataSource

@RestController
@RequestMapping("/api/health")
class HealthController(
    private val dataSource: DataSource,
    private val redisTemplate: RedisTemplate<String, Any>?,
) {
    @GetMapping
    fun health(): Map<String, Any> {
        val checks = mutableMapOf<String, Any>("status" to "ok", "timestamp" to java.time.Instant.now())

        // DB check
        checks["database"] = try {
            dataSource.connection.use { it.isValid(2) }
            "ok"
        } catch (e: Exception) { "error: ${e.message}" }

        // Redis check
        checks["redis"] = try {
            redisTemplate?.let {
                it.connectionFactory!!.connection.ping()
                "ok"
            } ?: "not configured"
        } catch (e: Exception) { "error: ${e.message}" }

        return checks
    }
}
