package com.valoracloud.api.contabo

import com.fasterxml.jackson.annotation.JsonProperty

// ─── OAuth2 Token ────────────────────────────────────────

data class ContaboTokenResponse(
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("token_type") val tokenType: String,
    @JsonProperty("expires_in") val expiresIn: Int,
    val scope: String,
)

// ─── Generic List Response ───────────────────────────────

data class ContaboListResponse<T>(
    val data: List<T>,
    @JsonProperty("_links") val links: ContaboLinks,
    @JsonProperty("_pagination") val pagination: ContaboPagination,
)

data class ContaboLinks(
    val self: String,
    val first: String,
    val last: String,
)

data class ContaboPagination(
    val size: Int,
    val totalElements: Int,
    val totalPages: Int,
    val page: Int,
)

// ─── Compute Instances ───────────────────────────────────

data class ContaboInstance(
    val instanceId: Long,
    val name: String?,
    val displayName: String?,
    val status: String,
    val imageId: String?,
    val region: String?,
    val productId: String?,
    val ipConfig: ContaboIpConfig?,
    val createdDate: String?,
    val defaultUser: String?,
)

data class ContaboIpConfig(
    val v4: ContaboIpV4,
)

data class ContaboIpV4(
    val ip: String,
    val gateway: String,
    val netmaskCidr: Int,
)

data class ContaboCreateInstanceRequest(
    val imageId: String,
    val productId: String,
    val region: String,
    val period: Long = 1,
    val displayName: String? = null,
    val sshKeys: List<Long>? = null,
    val rootPassword: Long? = null,
    val defaultUser: String? = null,
    val userData: String? = null,
)

data class ContaboVncAccess(
    val host: String,
    val port: Int,
    val password: String,
)

// ─── Images ──────────────────────────────────────────────

data class ContaboImage(
    val imageId: String,
    val name: String,
    val description: String?,
    val osType: String?,
    val version: String?,
    val standardImage: Boolean? = null,
    val url: String? = null,
    val status: String? = null,
    val sizeGb: Long? = null,
    val createdDate: String? = null,
)

data class ContaboCreateCustomImageRequest(
    val name: String,
    val description: String? = null,
    val url: String,
    val osType: OsType,
    val version: String,
)

enum class OsType {
    @JsonProperty("Windows") WINDOWS,
    @JsonProperty("Linux") LINUX,
}

data class ContaboUpdateImageRequest(
    val name: String? = null,
    val description: String? = null,
)

data class ContaboImageStats(
    val customImagesCount: Int,
    val totalAvailableSpaceGB: Long,
    val usedSpaceGB: Long,
    val freeSpaceGB: Long,
)

// ─── Snapshots ───────────────────────────────────────────

data class ContaboSnapshot(
    val snapshotId: String,
    val instanceId: Long,
    val name: String,
    val description: String? = null,
    val createdDate: String? = null,
)

data class ContaboCreateSnapshotRequest(
    val name: String,
    val description: String? = null,
)

data class ContaboUpdateSnapshotRequest(
    val name: String? = null,
    val description: String? = null,
)

// ─── Private Networks ────────────────────────────────────

data class ContaboPrivateNetwork(
    val privateNetworkId: Long,
    val name: String,
    val description: String? = null,
    val region: String?,
    val dataCenter: String? = null,
    val availableIps: Int? = null,
    val cidr: String? = null,
    val createdDate: String? = null,
    val instances: List<Long>? = null,
)

data class ContaboCreatePrivateNetworkRequest(
    val region: String = "EU",
    val name: String,
    val description: String? = null,
)

data class ContaboUpdatePrivateNetworkRequest(
    val name: String? = null,
    val description: String? = null,
)

// ─── Firewalls ───────────────────────────────────────────

data class ContaboFirewall(
    val firewallId: String,
    val name: String,
    val description: String? = null,
    val status: FirewallStatus,
    val createdDate: String? = null,
    val instances: List<Long>? = null,
    val rules: ContaboFirewallRules? = null,
)

data class ContaboFirewallRules(
    val inbound: List<ContaboFirewallRule>? = null,
)

data class ContaboFirewallRule(
    val protocol: FirewallProtocol,
    val port: Int? = null,
    val portRange: String? = null,
    val sourceIp: String? = null,
    val sourceNet: String? = null,
    val action: FirewallAction? = null,
)

enum class FirewallProtocol {
    TCP, UDP, ICMP
}

enum class FirewallAction {
    @JsonProperty("allow") ALLOW,
    @JsonProperty("deny") DENY,
}

enum class FirewallStatus {
    @JsonProperty("active") ACTIVE,
    @JsonProperty("inactive") INACTIVE,
}

data class ContaboCreateFirewallRequest(
    val name: String,
    val description: String? = null,
    val status: FirewallStatus,
    val rules: ContaboFirewallRules? = null,
)

data class ContaboUpdateFirewallRequest(
    val name: String? = null,
    val description: String? = null,
    val status: FirewallStatus? = null,
)

data class ContaboUpdateFirewallRulesRequest(
    val rules: ContaboFirewallRules,
)

data class ContaboPresetRule(
    val name: String,
    val rules: ContaboFirewallRules,
)

// ─── VIP (Virtual IP Addresses) ──────────────────────────

data class ContaboVip(
    val ip: String,
    val resourceId: Any? = null,
    val resourceType: String? = null,
    val resourceName: String? = null,
    val resourceDisplayName: String? = null,
    val ipVersion: String,
    val type: VipType,
    val dataCenter: String? = null,
    val region: String? = null,
)

enum class VipType {
    @JsonProperty("additional") ADDITIONAL,
    @JsonProperty("floating") FLOATING,
}

// ─── Secrets ─────────────────────────────────────────────

enum class ContaboSecretType {
    ssh, password
}

data class ContaboSecret(
    val secretId: Long,
    val name: String,
    val type: ContaboSecretType,
    val value: String? = null,
    val createdDate: String? = null,
    val updatedDate: String? = null,
)

data class ContaboCreateSecretRequest(
    val name: String,
    val type: ContaboSecretType,
    val value: String,
)

data class ContaboUpdateSecretRequest(
    val name: String? = null,
    val value: String? = null,
)

// ─── Tags ────────────────────────────────────────────────

data class ContaboTag(
    val tagId: Long,
    val name: String,
    val color: String? = null,
)

data class ContaboCreateTagRequest(
    val name: String,
    val color: String,
)

data class ContaboUpdateTagRequest(
    val name: String? = null,
    val color: String? = null,
)

data class ContaboTagAssignment(
    val tagId: Long,
    val resourceType: TagResourceType,
    val resourceId: String,
    val resourceName: String? = null,
)

enum class TagResourceType {
    @JsonProperty("instance") INSTANCE,
    @JsonProperty("image") IMAGE,
    @JsonProperty("object-storage") OBJECT_STORAGE,
    @JsonProperty("snapshot") SNAPSHOT,
    @JsonProperty("private-network") PRIVATE_NETWORK,
    @JsonProperty("firewall") FIREWALL,
    @JsonProperty("vip") VIP,
}

// ─── Object Storage ──────────────────────────────────────

data class ContaboObjectStorage(
    val objectStorageId: String,
    val dataCenter: String?,
    val region: String?,
    val totalPurchasedSpaceTB: Double,
    val displayName: String? = null,
    val autoScaling: ContaboAutoScaling? = null,
    val s3Url: String?,
    val s3TenantId: String?,
    val status: String?,
    val createdDate: String?,
)

data class ContaboAutoScaling(
    val state: AutoScalingState,
    val sizeLimitTB: Double,
)

enum class AutoScalingState {
    @JsonProperty("enabled") ENABLED,
    @JsonProperty("disabled") DISABLED,
}

data class ContaboS3Credentials(
    val accessKey: String,
    val secretKey: String,
    val region: String,
    val s3Url: String,
)

data class ContaboObjectStorageStats(
    val objectStorageId: String?,
    val displayName: String?,
    val usedSpaceTB: Double,
    val totalPurchasedSpaceTB: Double,
)

data class ContaboCreateObjectStorageRequest(
    val region: String,
    val totalPurchasedSpaceTB: Double,
    val displayName: String? = null,
    val autoScaling: ContaboAutoScaling? = null,
)

data class ContaboUpgradeObjectStorageRequest(
    val totalPurchasedSpaceTB: Double? = null,
    val autoScaling: ContaboAutoScaling? = null,
)

data class ContaboCancelObjectStorageRequest(
    val cancelDate: String,
)

// ─── Domains ─────────────────────────────────────────────

/**
 * Nameserver wire format used by Contabo's API (hostname field).
 * @internal — only used inside ContaboService.
 */
data class ContaboNameserverWire(
    val hostname: String,
    val ip: String? = null,
)

/** Application-friendly nameserver shape. */
data class ContaboNameserver(
    val name: String,
    val ip: String? = null,
)

/** @internal — raw domain response shape from Contabo. */
data class ContaboDomainWire(
    val domain: String,
    val authCode: String? = null,
    val status: String,
    val createdDate: String,
    val expirationDate: String,
    val autoRenew: Boolean,
    val handles: ContaboDomainHandlesWire,
    val nameservers: List<ContaboNameserverWire>,
)

data class ContaboDomainHandlesWire(
    val owner: String,
    val admin: String,
    val tech: String,
    val zone: String,
)

/** Application-friendly domain shape (nameservers mapped to ContaboNameserver). */
data class ContaboDomain(
    val domain: String,
    val authCode: String? = null,
    val status: String,
    val createdDate: String,
    val expirationDate: String,
    val autoRenew: Boolean,
    val handles: ContaboDomainHandlesWire,
    val nameservers: List<ContaboNameserver>,
)

data class ContaboCreateDomainRequest(
    val domain: String,
    val authCode: String? = null,
    val handles: ContaboDomainHandlesWire,
    val nameservers: List<ContaboNameserver>,
    val resourceType: String? = null,
    val resourceId: String? = null,
)

data class ContaboUpdateDomainRequest(
    val nameservers: List<ContaboNameserver>? = null,
    val handles: ContaboUpdateDomainHandlesRequest? = null,
)

data class ContaboUpdateDomainHandlesRequest(
    val owner: String? = null,
    val admin: String? = null,
    val tech: String? = null,
    val zone: String? = null,
)

data class ContaboDomainAvailability(
    val domain: String,
    val available: Boolean,
    val price: Double? = null,
)

data class ContaboAuthCodeResponse(
    val authCode: String,
)

// ─── Domain Handles ──────────────────────────────────────

/** Application-friendly domain handle (phone uses countryCode/subscriberNumber). */
data class ContaboDomainHandle(
    val handleId: String,
    val handleType: HandleType,
    val firstName: String,
    val lastName: String,
    val organization: String? = null,
    val email: String,
    val gender: Gender? = null,
    val birthInfo: ContaboBirthInfo? = null,
    val address: ContaboHandleAddress,
    val phone: ContaboHandlePhone,
    val fax: String? = null,
    val createdDate: String,
)

data class ContaboCreateDomainHandleRequest(
    val handleType: HandleType,
    val firstName: String,
    val lastName: String,
    val organization: String? = null,
    val email: String,
    val gender: Gender? = null,
    val birthInfo: ContaboBirthInfo? = null,
    val address: ContaboHandleAddress,
    val phone: ContaboHandlePhone,
    val fax: String? = null,
)

/** @internal — Contabo API wire format for domain handles. Uses phone.prefix/phone.number. */
data class ContaboHandleWire(
    val handleId: String? = null,
    val createdDate: String? = null,
    val handleType: HandleType,
    val firstName: String,
    val lastName: String,
    val organization: String? = null,
    val email: String,
    val gender: Gender? = null,
    val birthInfo: ContaboBirthInfo? = null,
    val address: ContaboHandleAddress,
    val phone: ContaboHandleWirePhone,
    val fax: ContaboHandleWireFax? = null,
)

data class ContaboHandleWirePhone(
    val prefix: String,
    val number: String,
)

data class ContaboHandleWireFax(
    val prefix: String,
    val number: String,
)

enum class HandleType {
    @JsonProperty("person") PERSON,
    @JsonProperty("organization") ORGANIZATION,
}

enum class Gender {
    @JsonProperty("male") MALE,
    @JsonProperty("female") FEMALE,
    @JsonProperty("na") NA,
}

data class ContaboBirthInfo(
    val date: String,
    val city: String,
    val country: String,
    val zipCode: String? = null,
    val province: String? = null,
)

data class ContaboHandleAddress(
    val street: String,
    val streetNumber: String,
    val zipCode: String,
    val city: String,
    val country: String,
)

data class ContaboHandlePhone(
    val countryCode: String,
    val areaCode: String? = null,
    val subscriberNumber: String,
)

// ─── DNS ─────────────────────────────────────────────────

data class ContaboDnsZone(
    val zoneName: String,
    val registrar: String?,
    val updatedAt: String?,
)

data class ContaboDnsRecord(
    val recordId: String,
    val name: String,
    val type: String,
    val data: String,
    val ttl: Int,
)

data class ContaboCreateDnsRecordRequest(
    val name: String,
    val type: String,
    val data: String,
    val ttl: Int? = null,
)

// ─── Reverse DNS ─────────────────────────────────────────

data class ContaboReverseDnsRequest(
    val ptr: String,
)

// ─── Domain Cancellation ─────────────────────────────────

data class ContaboCancelDomainRequest(
    val reason: String,
    val cancelDate: String,
)