package com.valoracloud.api.firewalls

import jakarta.validation.Valid
import jakarta.validation.constraints.*

// ─── Firewall Rule ──────────────────────────────────────
data class FirewallRuleDto(
    @field:NotBlank
    val protocol: String, // TCP, UDP, ICMP

    @field:Min(1) @field:Max(65535)
    val port: Int? = null,

    val portRange: String? = null, // e.g. "80-443"

    val sourceIp: String? = null,
    val sourceNet: String? = null,  // CIDR, e.g. "0.0.0.0/0"

    val action: String = "allow", // allow | deny
)

data class FirewallRulesDto(
    val inbound: List<@Valid FirewallRuleDto>? = null,
)

// ─── Create Firewall ────────────────────────────────────
data class CreateFirewallDto(
    @field:NotBlank @field:Size(min = 1, max = 255)
    val name: String,

    @field:Size(max = 255)
    val description: String? = null,

    @field:NotBlank
    val status: String = "active", // active | inactive

    @field:Valid
    val rules: FirewallRulesDto? = null,
)

// ─── Update Firewall ────────────────────────────────────
data class UpdateFirewallDto(
    @field:Size(min = 1, max = 255)
    val name: String? = null,

    @field:Size(max = 255)
    val description: String? = null,

    val status: String? = null, // active | inactive
)

// ─── Update Firewall Rules ──────────────────────────────
data class UpdateFirewallRulesDto(
    @field:Valid
    @field:NotNull
    val rules: FirewallRulesDto,
)
