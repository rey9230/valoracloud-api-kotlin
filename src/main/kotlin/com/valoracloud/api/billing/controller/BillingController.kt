package com.valoracloud.api.billing.controller

import com.valoracloud.api.billing.service.BillingService
import com.valoracloud.api.shkeeper.SHKeeperCallback
import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/billing")
@Tag(name = "Billing")
class BillingController(
    private val billingService: BillingService,
) {

    /**
     * Stripe webhook endpoint.
     * The raw body is consumed via HttpServletRequest.getReader() to preserve
     * the exact bytes for signature verification.
     */
    @PostMapping("/webhook")
    @ResponseStatus(HttpStatus.OK)
    @Hidden
    fun handleWebhook(
        request: HttpServletRequest,
        @RequestHeader("stripe-signature") signature: String,
    ): ResponseEntity<Map<String, Any>> {
        val rawBody = request.reader.readText()
        val result = billingService.handleWebhook(rawBody, signature)
        return ResponseEntity.ok(result)
    }

    /**
     * SHKeeper callback endpoint.
     * SHKeeper sends POST requests here when a crypto payment is received.
     * Returns HTTP 202 to prevent SHKeeper from retrying.
     */
    @PostMapping("/crypto-webhook")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Hidden
    fun handleCryptoWebhook(
        @RequestHeader("x-shkeeper-api-key") apiKey: String,
        @RequestBody payload: SHKeeperCallback,
    ): Map<String, Any> {
        return billingService.handleCryptoWebhook(apiKey, payload)
    }
}
