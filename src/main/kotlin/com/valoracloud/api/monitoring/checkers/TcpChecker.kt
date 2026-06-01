package com.valoracloud.api.monitoring.checkers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

data class TcpCheckResult(
    val tcpOpen: Boolean,
    val pingMs: Int?,
    val errorMessage: String?,
)

suspend fun checkTcp(ip: String, port: Int, timeoutMs: Int = 5000): TcpCheckResult =
    withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                val elapsed = (System.currentTimeMillis() - startTime).toInt()
                TcpCheckResult(tcpOpen = true, pingMs = elapsed, errorMessage = null)
            }
        } catch (e: Exception) {
            val elapsed = if (e is java.net.SocketTimeoutException) null
            else (System.currentTimeMillis() - startTime).toInt()
            TcpCheckResult(
                tcpOpen = false,
                pingMs = elapsed,
                errorMessage = when {
                    e is java.net.SocketTimeoutException -> "TCP connection timeout"
                    else -> e.message
                },
            )
        }
    }
