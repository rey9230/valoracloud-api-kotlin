package com.valoracloud.api.monitoring.checkers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class SslCheckResult(
    val sslValid: Boolean,
    val sslDaysUntilExpiry: Int?,
    val sslIssuer: String?,
    val errorMessage: String?,
)

suspend fun checkSsl(hostname: String, port: Int = 443): SslCheckResult =
    withContext(Dispatchers.IO) {
        try {
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, null, java.security.SecureRandom())
            val factory = sslContext.socketFactory

            factory.createSocket(hostname, port).use { socket ->
                socket.soTimeout = 8000
                (socket as javax.net.ssl.SSLSocket).startHandshake()
                val session = socket.session
                val certs = session.peerCertificates
                if (certs.isEmpty()) {
                    return@withContext SslCheckResult(false, null, null, "No peer certificates found")
                }

                val cert = certs[0] as X509Certificate
                val expiresAt = cert.notAfter.toInstant()
                val now = Instant.now()
                val daysUntilExpiry = java.time.Duration.between(now, expiresAt).toDays().toInt()
                val issuer = cert.issuerX500Principal.name.let {
                    val o = it.split(",").find { s -> s.trim().startsWith("O=") }
                        ?.substringAfter("O=")?.trim()
                    val cn = it.split(",").find { s -> s.trim().startsWith("CN=") }
                        ?.substringAfter("CN=")?.trim()
                    o ?: cn ?: "Unknown"
                }

                SslCheckResult(
                    sslValid = daysUntilExpiry > 0,
                    sslDaysUntilExpiry = daysUntilExpiry,
                    sslIssuer = issuer,
                    errorMessage = if (daysUntilExpiry <= 0) "SSL certificate expired" else null,
                )
            }
        } catch (e: java.net.SocketTimeoutException) {
            SslCheckResult(false, null, null, "SSL connection timeout")
        } catch (e: Exception) {
            SslCheckResult(false, null, null, e.message)
        }
    }
