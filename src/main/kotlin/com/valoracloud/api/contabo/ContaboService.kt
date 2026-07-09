package com.valoracloud.api.contabo

import com.fasterxml.jackson.databind.ObjectMapper
import com.valoracloud.api.common.exceptions.AppException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.time.Duration
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.TimeUnit

@Service
class ContaboService(
    private val redisTemplate: RedisTemplate<String, Any>,
    private val objectMapper: ObjectMapper,
    @Value("\${app.contabo.api-url:https://api.contabo.com}") private val apiUrl: String,
    @Value("\${app.contabo.client-id:}") private val clientId: String,
    @Value("\${app.contabo.client-secret:}") private val clientSecret: String,
    @Value("\${app.contabo.api-user:}") private val apiUser: String,
    @Value("\${app.contabo.api-password:}") private val apiPassword: String,
    @Value("\${app.contabo.auth-url:https://auth.contabo.com/auth/realms/contabo/protocol/openid-connect/token}")
    private val authUrl: String,
) {

    private val logger = LoggerFactory.getLogger(ContaboService::class.java)

    private val webClient: WebClient = WebClient.builder()
        .codecs { configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024) }
        .build()

    // ─── In-Memory Token Cache (fallback when Redis is unavailable) ──

    private data class CachedToken(val token: String, val expiresAt: Long)
    @Volatile private var tokenCache: CachedToken? = null

    companion object {
        private const val TOKEN_CACHE_KEY = "contabo:access_token"
        private const val DEFAULT_TIMEOUT_MS = 15_000L
        private const val DOMAIN_TIMEOUT_MS = 60_000L
    }

    // ─── OAuth2 Token Management ─────────────────────────────────────

    private fun getAccessToken(): String {
        // 1. In-memory fallback cache
        tokenCache?.let { cached ->
            if (System.currentTimeMillis() < cached.expiresAt) {
                return cached.token
            }
        }

        // 2. Try Redis cache
        try {
            val cached = redisTemplate.opsForValue().get(TOKEN_CACHE_KEY)
            if (cached is String && cached.isNotBlank()) {
                return cached
            }
        } catch (e: Exception) {
            logger.warn("Redis unavailable for token cache (falling back to direct fetch): ${e.message}")
        }

        // 3. Request new token from Contabo
        val body = "client_id=$clientId" +
            "&client_secret=$clientSecret" +
            "&username=$apiUser" +
            "&password=$apiPassword" +
            "&grant_type=password"

        return try {
            val response = webClient.post()
                .uri(authUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(ContaboTokenResponse::class.java)
                .block(Duration.ofSeconds(10))
                ?: throw AppException(HttpStatus.BAD_GATEWAY, "Contabo OAuth2 returned null response")

            val ttl = maxOf(response.expiresIn - 60, 60)
            val token = response.accessToken

            // Store in in-memory cache
            tokenCache = CachedToken(token, System.currentTimeMillis() + ttl * 1000L)

            // Try Redis (best-effort)
            try {
                redisTemplate.opsForValue().set(TOKEN_CACHE_KEY, token, ttl.toLong(), TimeUnit.SECONDS)
            } catch (e: Exception) {
                logger.warn("Redis unavailable for token cache write: ${e.message}")
            }

            token
        } catch (e: WebClientResponseException) {
            logger.error("Contabo OAuth2 failed: ${e.statusCode} ${e.responseBodyAsString}")
            throw AppException(HttpStatus.BAD_GATEWAY, "Failed to authenticate with Contabo")
        } catch (e: Exception) {
            logger.error("Contabo OAuth2 unexpected error: ${e.message}", e)
            throw AppException(HttpStatus.BAD_GATEWAY, "Failed to authenticate with Contabo")
        }
    }

    // ─── API Request Helpers ──────────────────────────────────────────

    /**
     * Makes a Contabo API request expecting a JSON object response.
     */
    private fun <T : Any> apiRequest(
        method: String,
        path: String,
        body: Any? = null,
        responseType: Class<T>,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): T {
        val token = getAccessToken()
        val url = "$apiUrl/v1$path"
        val requestId = UUID.randomUUID().toString()

        val requestSpec = webClient.method(org.springframework.http.HttpMethod.valueOf(method))
            .uri(url)
            .header("Authorization", "Bearer $token")
            .header("x-request-id", requestId)
            .accept(MediaType.APPLICATION_JSON)

        if (body != null) {
            requestSpec.contentType(MediaType.APPLICATION_JSON)
            requestSpec.bodyValue(body)
        }

        return try {
            requestSpec
                .retrieve()
                .onStatus({ it.isError }) { errorResponse ->
                    errorResponse.bodyToMono(String::class.java)
                        .defaultIfEmpty("")
                        .flatMap { errorBody ->
                            Mono.error(buildContaboException(errorResponse.statusCode().value(), errorBody))
                        }
                }
                .bodyToMono(responseType)
                .block(Duration.ofMillis(timeoutMs))
                ?: throw AppException(HttpStatus.BAD_GATEWAY, "Contabo API returned null response for $method $path")
        } catch (e: ContaboApiException) {
            logger.error("Contabo API $method $path failed: ${e.statusCode} ${e.message}")
            throw e
        } catch (e: WebClientRequestException) {
            logger.error("Contabo API $method $path timed out after ${timeoutMs / 1000}s")
            throw AppException(HttpStatus.GATEWAY_TIMEOUT, "Contabo API request timed out")
        } catch (e: AppException) {
            throw e
        } catch (e: Exception) {
            logger.error("Contabo API $method $path unexpected error: ${e.message}", e)
            throw AppException(HttpStatus.BAD_GATEWAY, "Contabo API request failed: ${e.message}")
        }
    }

    /**
     * Makes a Contabo API request extracting the first element from { data: [...] } wrapper.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> apiRequestSingle(
        method: String,
        path: String,
        body: Any? = null,
        targetType: Class<T>,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): T {
        val response = apiRequest(
            method, path, body,
            Map::class.java as Class<Map<String, Any?>>,
            timeoutMs,
        )
        val dataList = response["data"] as? List<*> ?: throw AppException(
            HttpStatus.BAD_GATEWAY, "Contabo API response missing 'data' array for $method $path"
        )
        val first = dataList.firstOrNull() ?: throw AppException(
            HttpStatus.BAD_GATEWAY, "Contabo API response 'data' array is empty for $method $path"
        )
        return objectMapper.convertValue(first, targetType)
    }

    /**
     * Makes a Contabo API request extracting the list from { data: [...] } wrapper.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> apiRequestList(
        method: String,
        path: String,
        body: Any? = null,
        itemType: Class<T>,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): List<T> {
        val response = apiRequest(
            method, path, body,
            Map::class.java as Class<Map<String, Any?>>,
            timeoutMs,
        )
        val dataList = response["data"] as? List<*> ?: emptyList<Any>()
        return dataList.map { objectMapper.convertValue(it, itemType) }
    }

    /**
     * Makes a Contabo API request expecting no response body (204 No Content).
     */
    private fun apiRequestVoid(
        method: String,
        path: String,
        body: Any? = null,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ) {
        val token = getAccessToken()
        val url = "$apiUrl/v1$path"
        val requestId = UUID.randomUUID().toString()

        val requestSpec = webClient.method(org.springframework.http.HttpMethod.valueOf(method))
            .uri(url)
            .header("Authorization", "Bearer $token")
            .header("x-request-id", requestId)

        if (body != null) {
            requestSpec.contentType(MediaType.APPLICATION_JSON)
            requestSpec.bodyValue(body)
        }

        try {
            requestSpec
                .retrieve()
                .onStatus({ it.isError }) { errorResponse ->
                    errorResponse.bodyToMono(String::class.java)
                        .defaultIfEmpty("")
                        .flatMap { errorBody ->
                            Mono.error(buildContaboException(errorResponse.statusCode().value(), errorBody))
                        }
                }
                .toBodilessEntity()
                .block(Duration.ofMillis(timeoutMs))
        } catch (e: ContaboApiException) {
            logger.error("Contabo API $method $path failed: ${e.statusCode} ${e.message}")
            throw e
        } catch (e: WebClientRequestException) {
            logger.error("Contabo API $method $path timed out after ${timeoutMs / 1000}s")
            throw AppException(HttpStatus.GATEWAY_TIMEOUT, "Contabo API request timed out")
        } catch (e: AppException) {
            throw e
        } catch (e: Exception) {
            logger.error("Contabo API $method $path unexpected error: ${e.message}", e)
            throw AppException(HttpStatus.BAD_GATEWAY, "Contabo API request failed: ${e.message}")
        }
    }

    private fun buildContaboException(statusCode: Int, errorBody: String): ContaboApiException {
        val message = try {
            val node = objectMapper.readTree(errorBody)
            node.get("message")?.asText() ?: "Contabo API error: $statusCode"
        } catch (_: Exception) {
            "Contabo API error: $statusCode"
        }
        return ContaboApiException(statusCode, message, errorBody)
    }

    // ══════════════════════════════════════════════════════════════════
    //  INSTANCE OPERATIONS
    // ══════════════════════════════════════════════════════════════════

    fun createInstance(
        productId: String,
        region: String,
        imageId: String,
        displayName: String,
        period: Long = 1,
        rootPassword: Long? = null,
        sshKeys: List<Long>? = null,
        userData: String? = null,
        // Linux is always root; only Windows callers pass "administrator".
        defaultUser: String = "root",
        license: String? = null,
        addOns: ContaboCreateInstanceAddOns? = null,
    ): ContaboInstance {
        val req = ContaboCreateInstanceRequest(
            imageId = imageId,
            productId = productId,
            region = region,
            period = period,
            displayName = displayName,
            rootPassword = rootPassword,
            sshKeys = sshKeys,
            userData = userData,
            defaultUser = defaultUser,
            license = license,
            addOns = addOns,
        )
        return apiRequestSingle(HttpMethod.POST, "/compute/instances", req, ContaboInstance::class.java)
    }

    fun getInstance(instanceId: Long): ContaboInstance =
        apiRequestSingle(HttpMethod.GET, "/compute/instances/$instanceId", targetType = ContaboInstance::class.java)

    fun listInstances(): List<ContaboInstance> =
        apiRequestList(HttpMethod.GET, "/compute/instances", itemType = ContaboInstance::class.java)

    fun startInstance(instanceId: Long) =
        apiRequestVoid(HttpMethod.POST, "/compute/instances/$instanceId/actions/start")

    fun stopInstance(instanceId: Long) =
        apiRequestVoid(HttpMethod.POST, "/compute/instances/$instanceId/actions/stop")

    fun restartInstance(instanceId: Long) =
        apiRequestVoid(HttpMethod.POST, "/compute/instances/$instanceId/actions/restart")

    fun reinstallInstance(
        instanceId: Long,
        imageId: String,
        rootPassword: Long? = null,
        userData: String? = null,
    ) {
        val body = mutableMapOf<String, Any?>("imageId" to imageId)
        rootPassword?.let { body["rootPassword"] = it }
        userData?.let { body["userData"] = it }
        apiRequestVoid(HttpMethod.PUT, "/compute/instances/$instanceId", body)
    }

    fun cancelInstance(instanceId: Long) =
        apiRequestVoid(HttpMethod.POST, "/compute/instances/$instanceId/cancel")

    fun getVncAccess(instanceId: Long): ContaboVncAccess =
        apiRequestSingle(HttpMethod.GET, "/compute/instances/$instanceId/vnc", targetType = ContaboVncAccess::class.java)

    // ══════════════════════════════════════════════════════════════════
    //  REVERSE DNS
    // ══════════════════════════════════════════════════════════════════

    fun setReverseDns(instanceId: Long, ip: String, rdns: String) {
        apiRequestVoid(HttpMethod.PUT, "/dns/ptrs/$ip", ContaboReverseDnsRequest(ptr = rdns))
    }

    // ══════════════════════════════════════════════════════════════════
    //  IMAGES
    // ══════════════════════════════════════════════════════════════════

    fun listImages(): List<ContaboImage> =
        apiRequestList(HttpMethod.GET, "/compute/images?size=100", itemType = ContaboImage::class.java)

    fun findImageBySlug(slug: String): ContaboImage? {
        val results = apiRequestList(
            HttpMethod.GET,
            "/compute/images?search=${URLEncoder.encode(slug, StandardCharsets.UTF_8)}&standardImage=true&size=10",
            itemType = ContaboImage::class.java,
        )
        if (results.isEmpty()) return null

        val lower = slug.lowercase()

        // Prefer exact name match
        results.find { it.name.lowercase() == lower }?.let { return it }

        // Prefer match that starts with the normalized slug
        val normalized = lower.replace(Regex("[-_.]"), " ")
        results.find { it.name.lowercase().startsWith(normalized) }?.let { return it }

        // Fallback to first result
        return results.firstOrNull()
    }

    fun createCustomImage(dto: ContaboCreateCustomImageRequest): ContaboImage =
        apiRequestSingle(HttpMethod.POST, "/compute/images", dto, ContaboImage::class.java)

    fun getImage(imageId: String): ContaboImage =
        apiRequestSingle(HttpMethod.GET, "/compute/images/$imageId", targetType = ContaboImage::class.java)

    fun updateImage(imageId: String, dto: ContaboUpdateImageRequest): ContaboImage =
        apiRequestSingle(HttpMethod.PATCH, "/compute/images/$imageId", dto, ContaboImage::class.java)

    fun deleteImage(imageId: String) =
        apiRequestVoid(HttpMethod.DELETE, "/compute/images/$imageId")

    fun getImageStats(): ContaboImageStats =
        apiRequestSingle(HttpMethod.GET, "/compute/images/stats", targetType = ContaboImageStats::class.java)

    // ══════════════════════════════════════════════════════════════════
    //  SNAPSHOTS
    // ══════════════════════════════════════════════════════════════════

    fun listSnapshots(instanceId: Long): List<ContaboSnapshot> =
        apiRequestList(HttpMethod.GET, "/compute/instances/$instanceId/snapshots", itemType = ContaboSnapshot::class.java)

    fun createSnapshot(instanceId: Long, dto: ContaboCreateSnapshotRequest): ContaboSnapshot =
        apiRequestSingle(HttpMethod.POST, "/compute/instances/$instanceId/snapshots", dto, ContaboSnapshot::class.java)

    fun getSnapshot(instanceId: Long, snapshotId: String): ContaboSnapshot =
        apiRequestSingle(
            HttpMethod.GET, "/compute/instances/$instanceId/snapshots/$snapshotId",
            targetType = ContaboSnapshot::class.java,
        )

    fun updateSnapshot(instanceId: Long, snapshotId: String, dto: ContaboUpdateSnapshotRequest): ContaboSnapshot =
        apiRequestSingle(
            HttpMethod.PATCH, "/compute/instances/$instanceId/snapshots/$snapshotId", dto,
            ContaboSnapshot::class.java,
        )

    fun deleteSnapshot(instanceId: Long, snapshotId: String) =
        apiRequestVoid(HttpMethod.DELETE, "/compute/instances/$instanceId/snapshots/$snapshotId")

    fun rollbackSnapshot(instanceId: Long, snapshotId: String) =
        apiRequestVoid(
            HttpMethod.POST, "/compute/instances/$instanceId/snapshots/$snapshotId/rollback",
            emptyMap<String, String>(),
        )

    // ══════════════════════════════════════════════════════════════════
    //  PRIVATE NETWORKS
    // ══════════════════════════════════════════════════════════════════

    fun listPrivateNetworks(): List<ContaboPrivateNetwork> =
        apiRequestList(HttpMethod.GET, "/private-networks", itemType = ContaboPrivateNetwork::class.java)

    fun createPrivateNetwork(dto: ContaboCreatePrivateNetworkRequest): ContaboPrivateNetwork =
        apiRequestSingle(HttpMethod.POST, "/private-networks", dto, ContaboPrivateNetwork::class.java)

    fun getPrivateNetwork(privateNetworkId: Long): ContaboPrivateNetwork =
        apiRequestSingle(HttpMethod.GET, "/private-networks/$privateNetworkId", targetType = ContaboPrivateNetwork::class.java)

    fun updatePrivateNetwork(privateNetworkId: Long, dto: ContaboUpdatePrivateNetworkRequest): ContaboPrivateNetwork =
        apiRequestSingle(HttpMethod.PATCH, "/private-networks/$privateNetworkId", dto, ContaboPrivateNetwork::class.java)

    fun deletePrivateNetwork(privateNetworkId: Long) =
        apiRequestVoid(HttpMethod.DELETE, "/private-networks/$privateNetworkId")

    fun assignInstanceToPrivateNetwork(privateNetworkId: Long, instanceId: Long) =
        apiRequestVoid(HttpMethod.POST, "/private-networks/$privateNetworkId/instances/$instanceId", emptyMap<String, String>())

    fun unassignInstanceFromPrivateNetwork(privateNetworkId: Long, instanceId: Long) =
        apiRequestVoid(HttpMethod.DELETE, "/private-networks/$privateNetworkId/instances/$instanceId")

    // ══════════════════════════════════════════════════════════════════
    //  FIREWALLS
    // ══════════════════════════════════════════════════════════════════

    fun listFirewalls(): List<ContaboFirewall> =
        apiRequestList(HttpMethod.GET, "/firewalls", itemType = ContaboFirewall::class.java)

    fun createFirewall(dto: ContaboCreateFirewallRequest): ContaboFirewall =
        apiRequestSingle(HttpMethod.POST, "/firewalls", dto, ContaboFirewall::class.java)

    fun getFirewall(firewallId: String): ContaboFirewall =
        apiRequestSingle(HttpMethod.GET, "/firewalls/$firewallId", targetType = ContaboFirewall::class.java)

    fun updateFirewall(firewallId: String, dto: ContaboUpdateFirewallRequest): ContaboFirewall =
        apiRequestSingle(HttpMethod.PATCH, "/firewalls/$firewallId", dto, ContaboFirewall::class.java)

    fun updateFirewallRules(firewallId: String, rulesDto: ContaboUpdateFirewallRulesRequest): ContaboFirewall =
        apiRequestSingle(HttpMethod.PUT, "/firewalls/$firewallId", rulesDto, ContaboFirewall::class.java)

    fun deleteFirewall(firewallId: String) =
        apiRequestVoid(HttpMethod.DELETE, "/firewalls/$firewallId")

    fun assignInstanceToFirewall(firewallId: String, instanceId: Long) =
        apiRequestVoid(HttpMethod.POST, "/firewalls/$firewallId/instances/$instanceId", emptyMap<String, String>())

    fun unassignInstanceFromFirewall(firewallId: String, instanceId: Long) =
        apiRequestVoid(HttpMethod.DELETE, "/firewalls/$firewallId/instances/$instanceId")

    fun getPresetRules(): List<ContaboPresetRule> =
        apiRequestList(HttpMethod.GET, "/firewalls/preset-rules", itemType = ContaboPresetRule::class.java)

    // ══════════════════════════════════════════════════════════════════
    //  VIP (Virtual IP Addresses)
    // ══════════════════════════════════════════════════════════════════

    fun listVips(): List<ContaboVip> =
        apiRequestList(HttpMethod.GET, "/vips", itemType = ContaboVip::class.java)

    fun getVip(ip: String): ContaboVip =
        apiRequestSingle(HttpMethod.GET, "/vips/$ip", targetType = ContaboVip::class.java)

    fun assignVipToInstance(ip: String, resourceType: String, resourceId: Long) =
        apiRequestVoid(HttpMethod.POST, "/vips/$ip/$resourceType/$resourceId")

    fun unassignVipFromInstance(ip: String, resourceType: String, resourceId: Long) =
        apiRequestVoid(HttpMethod.DELETE, "/vips/$ip/$resourceType/$resourceId")

    // ══════════════════════════════════════════════════════════════════
    //  OBJECT STORAGE
    // ══════════════════════════════════════════════════════════════════

    fun createObjectStorage(
        region: String,
        totalPurchasedSpaceTB: Double,
        displayName: String? = null,
        autoScaling: ContaboAutoScaling? = null,
    ): ContaboObjectStorage {
        val request = ContaboCreateObjectStorageRequest(
            region = region,
            totalPurchasedSpaceTB = totalPurchasedSpaceTB,
            displayName = displayName,
            autoScaling = autoScaling,
        )
        return apiRequestSingle(HttpMethod.POST, "/object-storages", request, ContaboObjectStorage::class.java)
    }

    fun getObjectStorage(objectStorageId: String): ContaboObjectStorage =
        apiRequestSingle(HttpMethod.GET, "/object-storages/$objectStorageId", targetType = ContaboObjectStorage::class.java)

    fun listObjectStorages(): List<ContaboObjectStorage> =
        apiRequestList(HttpMethod.GET, "/object-storages", itemType = ContaboObjectStorage::class.java)

    fun upgradeObjectStorage(objectStorageId: String, request: ContaboUpgradeObjectStorageRequest) =
        apiRequestVoid(HttpMethod.POST, "/object-storages/$objectStorageId/resize", request)

    fun cancelObjectStorage(objectStorageId: String, cancelDate: String) =
        apiRequestVoid(HttpMethod.PATCH, "/object-storages/$objectStorageId/cancel", ContaboCancelObjectStorageRequest(cancelDate = cancelDate))

    fun getObjectStorageStats(objectStorageId: String): ContaboObjectStorageStats =
        apiRequest(HttpMethod.GET, "/object-storages/$objectStorageId/stats", responseType = ContaboObjectStorageStats::class.java)

    fun getS3Credentials(userId: String): ContaboS3Credentials =
        apiRequestSingle(HttpMethod.GET, "/users/$userId/object-storages/credentials", targetType = ContaboS3Credentials::class.java)

    // ══════════════════════════════════════════════════════════════════
    //  DOMAINS
    // ══════════════════════════════════════════════════════════════════

    fun checkDomainAvailability(domain: String): ContaboDomainAvailability {
        // Contabo returns 204 (available) or 404 (not available) with no JSON body
        return try {
            apiRequestVoid(HttpMethod.POST, "/registries-domains/$domain/check-availability")
            ContaboDomainAvailability(domain = domain, available = true)
        } catch (e: ContaboApiException) {
            if (e.statusCode == 404) {
                ContaboDomainAvailability(domain = domain, available = false)
            } else {
                throw e
            }
        }
    }

    fun createDomain(request: ContaboCreateDomainRequest): ContaboDomain {
        // Build wire request with nameservers in Contabo's wire format
        val wireRequest = mutableMapOf<String, Any?>()
        wireRequest["domain"] = request.domain
        wireRequest["handles"] = request.handles
        wireRequest["nameservers"] = request.nameservers.map { ns ->
            ContaboNameserverWire(hostname = ns.name, ip = ns.ip)
        }
        request.authCode?.let { wireRequest["authCode"] = it }
        request.resourceType?.let { wireRequest["resourceType"] = it }
        request.resourceId?.let { wireRequest["resourceId"] = it }

        logger.info("Contabo createDomain request domain=${request.domain}")
        logger.debug("Contabo createDomain body: ${objectMapper.writeValueAsString(wireRequest)}")

        val wire = apiRequestSingle(
            HttpMethod.POST, "/domains", wireRequest,
            ContaboDomainWire::class.java,
            timeoutMs = DOMAIN_TIMEOUT_MS,
        )

        logger.info("Contabo createDomain response domain=${wire.domain} status=${wire.status}")
        return fromContaboDomainWire(wire)
    }

    fun getDomain(domain: String): ContaboDomain {
        val wire = apiRequestSingle(HttpMethod.GET, "/domains/$domain", targetType = ContaboDomainWire::class.java)
        return fromContaboDomainWire(wire)
    }

    fun listDomains(): List<ContaboDomain> {
        val wires = apiRequestList(HttpMethod.GET, "/domains", itemType = ContaboDomainWire::class.java)
        return wires.map { fromContaboDomainWire(it) }
    }

    fun updateDomain(domain: String, request: ContaboUpdateDomainRequest) {
        val body = mutableMapOf<String, Any?>()
        request.nameservers?.let { nameservers ->
            body["nameservers"] = nameservers.map { ns ->
                ContaboNameserverWire(hostname = ns.name, ip = ns.ip)
            }
        }
        request.handles?.let { handles ->
            body["handles"] = handles
        }
        apiRequestVoid(HttpMethod.PATCH, "/domains/$domain", body)
    }

    fun cancelDomain(domain: String, reason: String, cancelDate: String) =
        apiRequestVoid(HttpMethod.POST, "/domains/$domain/cancel", ContaboCancelDomainRequest(reason = reason, cancelDate = cancelDate))

    fun generateAuthCode(domain: String): String =
        apiRequestSingle(HttpMethod.POST, "/domains/$domain/generate-auth-code", targetType = ContaboAuthCodeResponse::class.java).authCode

    fun revokeDomainCancellation(domain: String) =
        apiRequestVoid(HttpMethod.POST, "/domains/$domain/revoke-cancellation")

    fun confirmTransferOut(domain: String) =
        apiRequestVoid(HttpMethod.POST, "/domains/$domain/transfer-out")

    fun revokeTransferOut(domain: String) =
        apiRequestVoid(HttpMethod.DELETE, "/domains/$domain/transfer-out")

    // ══════════════════════════════════════════════════════════════════
    //  DOMAIN HANDLES
    // ══════════════════════════════════════════════════════════════════

    fun createDomainHandle(request: ContaboCreateDomainHandleRequest): ContaboDomainHandle {
        val wire = toContaboHandleWire(request)
        logger.info("Contabo createDomainHandle email=${request.email}")
        logger.debug("Contabo createDomainHandle body: ${objectMapper.writeValueAsString(wire)}")

        val wireResp = apiRequestSingle(HttpMethod.POST, "/domains/handles", wire, ContaboHandleWire::class.java)
        logger.info("Contabo createDomainHandle response handleId=${wireResp.handleId}")
        return fromContaboHandleWire(wireResp)
    }

    fun getDomainHandle(handleId: String): ContaboDomainHandle {
        val wire = apiRequestSingle(HttpMethod.GET, "/domains/handles/$handleId", targetType = ContaboHandleWire::class.java)
        return fromContaboHandleWire(wire)
    }

    fun listDomainHandles(): List<ContaboDomainHandle> {
        val wires = apiRequestList(HttpMethod.GET, "/domains/handles?size=1000", itemType = ContaboHandleWire::class.java)
        return wires.map { fromContaboHandleWire(it) }
    }

    fun updateDomainHandle(handleId: String, request: ContaboCreateDomainHandleRequest) {
        val wire = toContaboHandleWire(request)
        // Contabo uses PUT (not PATCH) for handle updates
        apiRequestVoid(HttpMethod.PUT, "/domains/handles/$handleId", wire)
    }

    fun deleteDomainHandle(handleId: String) =
        apiRequestVoid(HttpMethod.DELETE, "/domains/handles/$handleId")

    fun getOrCreatePrivacyHandle(): String {
        val cached = redisTemplate.opsForValue().get(WhoisPrivacyConfig.CACHE_KEY)
        if (cached is String && cached.isNotBlank()) return cached

        val handle = createDomainHandle(WhoisPrivacyConfig.CONTACT)
        redisTemplate.opsForValue().set(WhoisPrivacyConfig.CACHE_KEY, handle.handleId)
        logger.info("WHOIS privacy handle created in Contabo: ${handle.handleId}")
        return handle.handleId
    }

    fun getCachedPrivacyHandleId(): String? {
        val cached = redisTemplate.opsForValue().get(WhoisPrivacyConfig.CACHE_KEY)
        return cached as? String
    }

    // ─── Handle wire-format helpers ──────────────────────────────────

    private fun toContaboHandleWire(req: ContaboCreateDomainHandleRequest): ContaboHandleWire {
        val wireFax = toContaboWireFax(req.fax)
        return ContaboHandleWire(
            handleType = req.handleType,
            firstName = req.firstName,
            lastName = req.lastName,
            organization = req.organization,
            email = req.email,
            gender = req.gender,
            birthInfo = req.birthInfo,
            address = req.address,
            phone = ContaboHandleWirePhone(
                prefix = req.phone.countryCode,
                number = joinContactNumber(req.phone.areaCode, req.phone.subscriberNumber),
            ),
            fax = wireFax,
        )
    }

    private fun fromContaboHandleWire(wire: ContaboHandleWire): ContaboDomainHandle =
        ContaboDomainHandle(
            handleId = wire.handleId ?: "",
            handleType = wire.handleType,
            firstName = wire.firstName,
            lastName = wire.lastName,
            organization = wire.organization,
            email = wire.email,
            gender = wire.gender,
            birthInfo = wire.birthInfo,
            address = wire.address,
            phone = ContaboHandlePhone(
                countryCode = wire.phone.prefix,
                subscriberNumber = wire.phone.number,
            ),
            fax = wire.fax?.let { "${it.prefix}${it.number}" },
            createdDate = wire.createdDate ?: "",
        )

    private fun fromContaboDomainWire(wire: ContaboDomainWire): ContaboDomain =
        ContaboDomain(
            domain = wire.domain,
            authCode = wire.authCode,
            status = wire.status,
            createdDate = wire.createdDate,
            expirationDate = wire.expirationDate,
            autoRenew = wire.autoRenew,
            handles = wire.handles,
            nameservers = wire.nameservers.map { ns ->
                ContaboNameserver(name = ns.hostname, ip = ns.ip)
            },
        )

    private fun joinContactNumber(areaCode: String?, subscriberNumber: String): String {
        val normalizedArea = areaCode?.trim()
        val normalizedSub = subscriberNumber.trim()
        return if (normalizedArea != null && normalizedArea.isNotEmpty()) {
            "$normalizedArea$normalizedSub"
        } else {
            normalizedSub
        }
    }

    private fun toContaboWireFax(fax: String?): ContaboHandleWireFax? {
        if (fax.isNullOrBlank()) return null

        val normalized = fax.trim()
        val match = Regex("""^(\+\d{1,4})([\d\s().-]+)$""").find(normalized)
        if (match == null) {
            logger.warn("Skipping fax for Contabo handle request — not international format: $normalized")
            return null
        }
        return ContaboHandleWireFax(
            prefix = match.groupValues[1],
            number = match.groupValues[2].replace(Regex("""\D"""), ""),
        )
    }

    // ══════════════════════════════════════════════════════════════════
    //  DNS MANAGEMENT
    // ══════════════════════════════════════════════════════════════════

    fun listDnsZones(): List<ContaboDnsZone> =
        apiRequestList(HttpMethod.GET, "/dns/zones", itemType = ContaboDnsZone::class.java)

    fun createDnsZone(zoneName: String): ContaboDnsZone =
        apiRequestSingle(HttpMethod.POST, "/dns/zones", mapOf("zoneName" to zoneName), ContaboDnsZone::class.java)

    fun getDnsZone(zoneName: String): ContaboDnsZone =
        apiRequestSingle(HttpMethod.GET, "/dns/zones/$zoneName", targetType = ContaboDnsZone::class.java)

    fun deleteDnsZone(zoneName: String) =
        apiRequestVoid(HttpMethod.DELETE, "/dns/zones/$zoneName")

    fun listDnsRecords(zoneName: String): List<ContaboDnsRecord> =
        apiRequestList(HttpMethod.GET, "/dns/zones/$zoneName/records", itemType = ContaboDnsRecord::class.java)

    fun createDnsRecord(zoneName: String, request: ContaboCreateDnsRecordRequest): ContaboDnsRecord =
        apiRequestSingle(HttpMethod.POST, "/dns/zones/$zoneName/records", request, ContaboDnsRecord::class.java)

    fun updateDnsRecord(zoneName: String, recordId: String, request: ContaboCreateDnsRecordRequest) =
        apiRequestVoid(HttpMethod.PATCH, "/dns/zones/$zoneName/records/$recordId", request)

    fun deleteDnsRecord(zoneName: String, recordId: String) =
        apiRequestVoid(HttpMethod.DELETE, "/dns/zones/$zoneName/records/$recordId")

    // ══════════════════════════════════════════════════════════════════
    //  SECRETS
    // ══════════════════════════════════════════════════════════════════

    fun listSecrets(type: ContaboSecretType? = null): List<ContaboSecret> {
        val query = if (type != null) "?type=${type.name}" else ""
        return apiRequestList(HttpMethod.GET, "/secrets$query", itemType = ContaboSecret::class.java)
    }

    fun getSecret(secretId: Long): ContaboSecret =
        apiRequestSingle(HttpMethod.GET, "/secrets/$secretId", targetType = ContaboSecret::class.java)

    fun createSecret(body: ContaboCreateSecretRequest): ContaboSecret =
        apiRequestSingle(HttpMethod.POST, "/secrets", body, ContaboSecret::class.java)

    fun updateSecret(secretId: Long, body: ContaboUpdateSecretRequest): ContaboSecret =
        apiRequestSingle(HttpMethod.PATCH, "/secrets/$secretId", body, ContaboSecret::class.java)

    fun deleteSecret(secretId: Long) =
        apiRequestVoid(HttpMethod.DELETE, "/secrets/$secretId")

    // ══════════════════════════════════════════════════════════════════
    //  TAGS
    // ══════════════════════════════════════════════════════════════════

    fun listTags(): List<ContaboTag> =
        apiRequestList(HttpMethod.GET, "/tags", itemType = ContaboTag::class.java)

    fun getTag(tagId: Long): ContaboTag =
        apiRequestSingle(HttpMethod.GET, "/tags/$tagId", targetType = ContaboTag::class.java)

    fun createTag(body: ContaboCreateTagRequest): ContaboTag =
        apiRequestSingle(HttpMethod.POST, "/tags", body, ContaboTag::class.java)

    fun updateTag(tagId: Long, body: ContaboUpdateTagRequest): ContaboTag =
        apiRequestSingle(HttpMethod.PATCH, "/tags/$tagId", body, ContaboTag::class.java)

    fun deleteTag(tagId: Long) =
        apiRequestVoid(HttpMethod.DELETE, "/tags/$tagId")

    fun listTagAssignments(tagId: Long): List<ContaboTagAssignment> =
        apiRequestList(HttpMethod.GET, "/tags/$tagId/assignments", itemType = ContaboTagAssignment::class.java)

    fun createTagAssignment(tagId: Long, resourceType: TagResourceType, resourceId: String): ContaboTagAssignment =
        apiRequestSingle(
            HttpMethod.POST, "/tags/$tagId/assignments/${resourceType.toApiValue()}/$resourceId",
            targetType = ContaboTagAssignment::class.java,
        )

    fun deleteTagAssignment(tagId: Long, resourceType: TagResourceType, resourceId: String) =
        apiRequestVoid(HttpMethod.DELETE, "/tags/$tagId/assignments/${resourceType.toApiValue()}/$resourceId")

    // ══════════════════════════════════════════════════════════════════
    //  HEALTH CHECK
    // ══════════════════════════════════════════════════════════════════

    fun isHealthy(): Boolean =
        try {
            getAccessToken()
            true
        } catch (_: Exception) {
            false
        }
}

// ══════════════════════════════════════════════════════════════════════
//  SUPPORTING TYPES
// ══════════════════════════════════════════════════════════════════════

private object HttpMethod {
    const val GET = "GET"
    const val POST = "POST"
    const val PUT = "PUT"
    const val PATCH = "PATCH"
    const val DELETE = "DELETE"
}

/**
 * Exception representing a Contabo API error response.
 */
class ContaboApiException(
    val statusCode: Int,
    message: String,
    val responseBody: String? = null,
) : AppException(
    status = when {
        statusCode >= 500 -> HttpStatus.BAD_GATEWAY
        statusCode == 404 -> HttpStatus.NOT_FOUND
        statusCode == 409 -> HttpStatus.CONFLICT
        statusCode == 400 -> HttpStatus.BAD_REQUEST
        else -> try { HttpStatus.valueOf(statusCode) } catch (_: Exception) { HttpStatus.BAD_GATEWAY }
    },
    message = message,
)

/**
 * Maps [TagResourceType] to the API path segment value (e.g., "object-storage", "private-network").
 */
fun TagResourceType.toApiValue(): String = when (this) {
    TagResourceType.INSTANCE -> "instance"
    TagResourceType.IMAGE -> "image"
    TagResourceType.OBJECT_STORAGE -> "object-storage"
    TagResourceType.SNAPSHOT -> "snapshot"
    TagResourceType.PRIVATE_NETWORK -> "private-network"
    TagResourceType.FIREWALL -> "firewall"
    TagResourceType.VIP -> "vip"
}
