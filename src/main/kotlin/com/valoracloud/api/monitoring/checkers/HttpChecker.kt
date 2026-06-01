package com.valoracloud.api.monitoring.checkers

import com.valoracloud.api.common.model.MonitorStatus
import kotlinx.coroutines.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.cert.X509Certificate
import java.time.Duration
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class HttpCheckResult(
    val status: MonitorStatus,
    val httpStatusCode: Int?,
    val httpResponseTimeMs: Int,
    val httpResponseBodySnippet: String?,
    val pingMs: Int?,
    val errorMessage: String?,
)

suspend fun checkHttp(url: String): HttpCheckResult = withContext(Dispatchers.IO) {
    val startTime = System.currentTimeMillis()

    // Accept self-signed certs (SSL checker handles validation separately)
    val sslContext = SSLContext.getInstance("TLS")
    val trustAll = object : X509TrustManager {
        override fun checkClientTrusted(c: Array<X509Certificate?>?, a: String?) {}
        override fun checkServerTrusted(c: Array<X509Certificate?>?, a: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }
    sslContext.init(null, arrayOf<TrustManager>(trustAll), java.security.SecureRandom())

    val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .sslContext(sslContext)
        .build()

    val request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("User-Agent", "ValoraCloud-Monitor/1.0")
        .timeout(Duration.ofSeconds(10))
        .GET()
        .build()

    try {
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        val elapsed = (System.currentTimeMillis() - startTime).toInt()
        val body = response.body()
        val snippet = body.take(500).ifBlank { null }
        val isUp = response.statusCode() in 200..399
        HttpCheckResult(
            status = if (isUp) MonitorStatus.UP else MonitorStatus.DEGRADED,
            httpStatusCode = response.statusCode(),
            httpResponseTimeMs = elapsed,
            httpResponseBodySnippet = snippet,
            pingMs = elapsed,
            errorMessage = if (isUp) null else "HTTP ${response.statusCode()}",
        )
    } catch (e: Exception) {
        val elapsed = (System.currentTimeMillis() - startTime).toInt()
        val status = when {
            e is java.net.http.HttpTimeoutException -> MonitorStatus.TIMEOUT
            else -> MonitorStatus.DOWN
        }
        HttpCheckResult(
            status = status,
            httpStatusCode = null,
            httpResponseTimeMs = elapsed,
            httpResponseBodySnippet = null,
            pingMs = null,
            errorMessage = e.message,
        )
    }
}
