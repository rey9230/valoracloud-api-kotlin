package com.valoracloud.api.monitoring.checkers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress

data class DnsCheckResult(
    val dnsResolved: Boolean,
    val dnsIpResolved: String?,
    val errorMessage: String?,
)

suspend fun checkDns(hostname: String): DnsCheckResult = withContext(Dispatchers.IO) {
    try {
        val addresses = InetAddress.getAllByName(hostname)
        if (addresses.isNotEmpty()) {
            DnsCheckResult(
                dnsResolved = true,
                dnsIpResolved = addresses[0].hostAddress,
                errorMessage = null,
            )
        } else {
            DnsCheckResult(false, null, "DNS resolved no addresses")
        }
    } catch (e: Exception) {
        DnsCheckResult(false, null, "DNS error: ${e.message}")
    }
}
