package com.valoracloud.api.monitoring.checkers

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class AgentMetrics(
    val cpu_percent: Double = 0.0,
    val ram_percent: Double = 0.0,
    val ram_used_mb: Int = 0,
    val ram_total_mb: Int = 0,
    val disk_percent: Double? = null,
    val disk_used_gb: Double? = null,
    val disk_total_gb: Double? = null,
    val load_avg_1m: Double = 0.0,
    val load_avg_5m: Double = 0.0,
    val load_avg_15m: Double = 0.0,
    val processes_count: Int = 0,
    val network_in_mbps: Double? = null,
    val network_out_mbps: Double? = null,
    val open_connections: Int? = null,
)

data class AgentCheckResult(
    val success: Boolean,
    val metrics: AgentMetrics? = null,
    val errorMessage: String? = null,
)

suspend fun checkAgent(ip: String, port: Int, secret: String): AgentCheckResult =
    withContext(Dispatchers.IO) {
        try {
            val client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build()

            val request = HttpRequest.newBuilder()
                .uri(URI.create("http://$ip:$port/metrics"))
                .header("x-agent-secret", secret)
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 401) {
                return@withContext AgentCheckResult(false, errorMessage = "Invalid agent secret")
            }

            val body = response.body()
            val mapper = jacksonObjectMapper()
            try {
                val metrics = mapper.readValue(body, AgentMetrics::class.java)
                AgentCheckResult(true, metrics = metrics)
            } catch (e: Exception) {
                AgentCheckResult(false, errorMessage = "Invalid agent response")
            }
        } catch (e: java.net.http.HttpTimeoutException) {
            AgentCheckResult(false, errorMessage = "Agent connection timeout")
        } catch (e: Exception) {
            AgentCheckResult(false, errorMessage = e.message)
        }
    }
