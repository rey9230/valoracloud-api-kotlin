package com.valoracloud.api.facebook

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.security.MessageDigest

/**
 * Facebook Conversions API integration using raw HTTP calls to
 * POST https://graph.facebook.com/v18.0/{pixelId}/events.
 *
 * Replaces the NestJS facebook-nodejs-business-sdk ServerEvent / EventRequest
 * pattern with direct REST requests.  PII fields are SHA‑256 hashed before
 * transmission.
 */
@Service
class FacebookConversionsService(
    private val restClientBuilder: RestClient.Builder,
    private val objectMapper: ObjectMapper,
    @Value("\${facebook.pixel-id:}") private val pixelId: String,
    @Value("\${facebook.access-token:}") private val accessToken: String,
    @Value("\${facebook.test-event-code:}") private val testEventCode: String,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val enabled: Boolean
        get() = pixelId.isNotBlank() && accessToken.isNotBlank()

    private val restClient: RestClient by lazy {
        restClientBuilder
            .baseUrl("https://graph.facebook.com/v18.0")
            .build()
    }

    // ── Public tracking methods ──────────────────────────────────────

    fun trackPurchase(params: PurchaseParams, ctx: FbClientContext? = null) =
        track("Purchase", params.orderId) {
            doTrack(
                eventName = "Purchase",
                userDataFields = userDataFields(params.email, ctx = ctx),
                customData = customDataMap {
                    put("value", params.value)
                    put("currency", params.currency)
                    put("content_ids", listOf(params.orderId))
                    put("content_type", "product")
                },
                ctx = ctx,
            )
        }

    fun trackSubscribe(params: SubscribeParams, ctx: FbClientContext? = null) =
        track("Subscribe", params.orderId) {
            doTrack(
                eventName = "Subscribe",
                userDataFields = userDataFields(params.email, ctx = ctx),
                customData = customDataMap {
                    put("value", params.value)
                    put("currency", params.currency)
                    put("content_ids", listOf(params.orderId))
                },
                ctx = ctx,
            )
        }

    fun trackCompleteRegistration(params: CompleteRegistrationParams, ctx: FbClientContext? = null) =
        track("CompleteRegistration", params.userId) {
            doTrack(
                eventName = "CompleteRegistration",
                userDataFields = userDataFields(
                    email = params.email,
                    firstName = params.firstName,
                    lastName = params.lastName,
                    ctx = ctx,
                ),
                customData = customDataMap {
                    put("content_name", "registration")
                    put("status", true)
                },
                ctx = ctx,
            )
        }

    fun trackInitiateCheckout(params: InitiateCheckoutParams, ctx: FbClientContext? = null) =
        track("InitiateCheckout", params.orderId) {
            doTrack(
                eventName = "InitiateCheckout",
                userDataFields = userDataFields(params.email, ctx = ctx),
                customData = customDataMap {
                    put("value", params.value)
                    put("currency", params.currency)
                    put("content_ids", listOf(params.orderId))
                },
                ctx = ctx,
            )
        }

    fun trackAddPaymentInfo(params: AddPaymentInfoParams, ctx: FbClientContext? = null) =
        track("AddPaymentInfo", params.orderId) {
            doTrack(
                eventName = "AddPaymentInfo",
                userDataFields = userDataFields(
                    email = params.email,
                    firstName = params.firstName,
                    lastName = params.lastName,
                    city = params.city,
                    state = params.state,
                    zipCode = params.zipCode,
                    ctx = ctx,
                ),
                customData = customDataMap {
                    put("content_category", "payment_info")
                },
                ctx = ctx,
            )
        }

    fun trackViewContent(params: ViewContentParams, ctx: FbClientContext? = null) =
        track("ViewContent", params.contentId) {
            doTrack(
                eventName = "ViewContent",
                userDataFields = userDataFields(params.email, ctx = ctx),
                customData = customDataMap {
                    put("content_ids", listOf(params.contentId))
                    put("content_type", "product")
                },
                ctx = ctx,
            )
        }

    fun trackSearch(params: SearchParams, ctx: FbClientContext? = null) =
        track("Search", params.searchString) {
            doTrack(
                eventName = "Search",
                userDataFields = userDataFields(params.email, ctx = ctx),
                customData = customDataMap {
                    put("search_string", params.searchString)
                },
                ctx = ctx,
            )
        }

    fun trackContact(params: ContactParams, ctx: FbClientContext? = null) =
        track("Contact", params.userId) {
            doTrack(
                eventName = "Contact",
                userDataFields = userDataFields(params.email, ctx = ctx),
                customData = customDataMap(),
                ctx = ctx,
            )
        }

    // ── Private helpers ──────────────────────────────────────────────

    /**
     * Wraps an event send in enabled-gate + try/catch so failures never
     * propagate into business logic.
     */
    private fun track(label: String, id: String, send: () -> Unit) {
        if (!enabled) {
            log.trace("Facebook disabled — skipping $label event [$id]")
            return
        }
        try {
            send()
            log.debug("Facebook $label event sent [$id]")
        } catch (e: Exception) {
            log.error("Failed to send Facebook $label event [$id]: ${e.message}", e)
        }
    }

    /**
     * Builds the full Conversions API payload and POSTs it.
     */
    private fun doTrack(
        eventName: String,
        userDataFields: MutableMap<String, Any>,
        customData: Map<String, Any?>,
        ctx: FbClientContext?,
    ) {
        // Inject client-side context into user_data
        ctx?.let {
            putIfNotBlank(userDataFields, "client_ip_address", it.clientIpAddress)
            putIfNotBlank(userDataFields, "client_user_agent", it.clientUserAgent)
            putIfNotBlank(userDataFields, "fbc", it.fbc)
            putIfNotBlank(userDataFields, "fbp", it.fbp)
        }

        val event = mutableMapOf<String, Any>(
            "event_name" to eventName,
            "event_time" to (System.currentTimeMillis() / 1000L).toInt(),
            "action_source" to "website",
            "user_data" to userDataFields,
            "custom_data" to customData.filterValues { it != null },
        )

        ctx?.eventSourceUrl?.let { event["event_source_url"] = it }

        val payload = mutableMapOf<String, Any>("data" to listOf(event))
        if (testEventCode.isNotBlank()) {
            payload["test_event_code"] = testEventCode
        }

        val body = objectMapper.writeValueAsString(payload)

        val response = restClient.post()
            .uri("/$pixelId/events?access_token=$accessToken")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .toEntity(String::class.java)

        if (log.isTraceEnabled) {
            log.trace(
                "Facebook Conversions API response [{}] — {}",
                response.statusCode.value(),
                response.body ?: "<empty>",
            )
        }
    }

    /**
     * Builds the user_data map from PII fields.  Every value is SHA‑256
     * hashed (lower-cased + trimmed before hashing where applicable) and
     * placed in an array as required by the Conversions API.
     */
    private fun userDataFields(
        email: String? = null,
        phone: String? = null,
        firstName: String? = null,
        lastName: String? = null,
        city: String? = null,
        state: String? = null,
        zipCode: String? = null,
        ctx: FbClientContext? = null,
    ): MutableMap<String, Any> {
        val fields = mutableMapOf<String, Any>()

        email?.let { fields["em"] = listOf(sha256(it.trim().lowercase())) }
        phone?.let { fields["ph"] = listOf(sha256(it.replace(Regex("[^0-9]"), ""))) }
        firstName?.let { fields["fn"] = listOf(sha256(it.trim().lowercase())) }
        lastName?.let { fields["ln"] = listOf(sha256(it.trim().lowercase())) }
        city?.let { fields["ct"] = listOf(sha256(it.trim().lowercase())) }
        state?.let { fields["st"] = listOf(sha256(it.trim().lowercase())) }
        zipCode?.let { fields["zp"] = listOf(sha256(it.trim().lowercase())) }

        return fields
    }

    // ── Utility ──────────────────────────────────────────────────────

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(value.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun customDataMap(init: MutableMap<String, Any?>.() -> Unit = {}): Map<String, Any?> {
        return mutableMapOf<String, Any?>().apply(init)
    }

    private fun putIfNotBlank(map: MutableMap<String, Any>, key: String, value: String?) {
        if (!value.isNullOrBlank()) map[key] = value
    }
}

// ── Client-side context ──────────────────────────────────────────────

/**
 * Browser / device context used to enrich Conversions API events.
 * Maps directly to the `user_data` sub‑fields:
 *   client_ip_address, client_user_agent, event_source_url, fbc, fbp.
 */
data class FbClientContext(
    val clientIpAddress: String? = null,
    val clientUserAgent: String? = null,
    val eventSourceUrl: String? = null,
    val fbc: String? = null,
    val fbp: String? = null,
)

// ── Event parameter data classes ────────────────────────────────────

data class PurchaseParams(
    val orderId: String,
    val value: Double,
    val currency: String,
    val email: String? = null,
)

data class SubscribeParams(
    val orderId: String,
    val value: Double,
    val currency: String,
    val email: String? = null,
)

data class CompleteRegistrationParams(
    val userId: String,
    val email: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
)

data class InitiateCheckoutParams(
    val orderId: String,
    val value: Double,
    val currency: String,
    val email: String? = null,
)

data class AddPaymentInfoParams(
    val orderId: String,
    val email: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val city: String? = null,
    val state: String? = null,
    val zipCode: String? = null,
)

data class ViewContentParams(
    val contentId: String,
    val email: String? = null,
)

data class SearchParams(
    val searchString: String,
    val email: String? = null,
)

data class ContactParams(
    val userId: String,
    val email: String? = null,
)
