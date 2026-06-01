package com.valoracloud.api.shkeeper

import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Service
class SHKeeperService(
    private val restClient: RestClient.Builder,
    @Value("\${shkeeper.base-url:https://gateway.valoracloud.com}") private val baseUrl: String,
    @Value("\${shkeeper.api-key:}") private val apiKey: String,
    @Value("\${shkeeper.enabled:false}") val enabled: Boolean,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun createInvoice(
        orderId: String,
        amountUsd: Double,
        cryptoCurrency: String,
        callbackUrl: String,
    ): SHKeeperInvoiceResponse {
        val url = "$baseUrl/api/v1/${encodeURI(cryptoCurrency)}/payment_request"
        val body = SHKeeperPaymentRequest(
            externalId = orderId,
            fiat = "USD",
            amount = "%.2f".format(amountUsd),
            callbackUrl = callbackUrl,
        )

        log.info("Creating SHKeeper invoice for order $orderId, crypto=$cryptoCurrency, amount=${body.amount}")

        val response = restClient.build()
            .post()
            .uri(url)
            .header("Content-Type", "application/json")
            .header("X-Shkeeper-Api-Key", apiKey)
            .body(body)
            .retrieve()
            .toEntity(SHKeeperInvoiceResponse::class.java)

        val data = response.body
            ?: throw RuntimeException("SHKeeper returned empty body")

        if (data.status != "success") {
            throw RuntimeException("SHKeeper error: $data")
        }

        return data
    }

    fun getApiKey(): String = apiKey

    private fun encodeURI(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8)
}

data class SHKeeperPaymentRequest(
    @JsonProperty("external_id") val externalId: String,
    val fiat: String,
    val amount: String,
    @JsonProperty("callback_url") val callbackUrl: String,
)

data class SHKeeperInvoiceResponse(
    val amount: String,
    @JsonProperty("display_name") val displayName: String,
    @JsonProperty("exchange_rate") val exchangeRate: String,
    val id: Long,
    @JsonProperty("recalculate_after") val recalculateAfter: Long,
    val status: String,
    val wallet: String,
)

data class SHKeeperCallback(
    @JsonProperty("external_id") val externalId: String,
    val crypto: String,
    val addr: String,
    val fiat: String,
    @JsonProperty("balance_fiat") val balanceFiat: String,
    @JsonProperty("balance_crypto") val balanceCrypto: String,
    val paid: Boolean,
    val status: String, // UNPAID | PARTIAL | PAID | OVERPAID
    val transactions: List<SHKeeperTransaction>,
    @JsonProperty("fee_percent") val feePercent: String,
    @JsonProperty("overpaid_fiat") val overpaidFiat: String,
)

data class SHKeeperTransaction(
    val txid: String,
    val date: String,
    @JsonProperty("amount_crypto") val amountCrypto: String,
    @JsonProperty("amount_fiat") val amountFiat: String,
    val trigger: Boolean,
    val crypto: String,
)
