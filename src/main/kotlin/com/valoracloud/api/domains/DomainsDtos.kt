package com.valoracloud.api.domains

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank

// ─── Check Domain ──────────────────────────────────────
data class CheckDomainDto(
    @field:NotBlank
    val domain: String,
)

// ─── Nameserver ────────────────────────────────────────
data class NameserverDto(
    @field:NotBlank
    val name: String,
    val ip: String? = null,
)

// ─── Checkout Domain ───────────────────────────────────
data class CheckoutDomainDto(
    @field:NotBlank
    val domain: String,

    @field:NotBlank
    val ownerHandleId: String,

    val adminHandleId: String? = null,
    val techHandleId: String? = null,
    val zoneHandleId: String? = null,

    @field:Valid
    val nameservers: List<NameserverDto> = emptyList(),

    val authCode: String? = null,
    val whoisPrivacy: Boolean = false,
    val paymentMethod: String? = null, // "stripe" | "crypto"
    val cryptoCurrency: String? = null,
)

// ─── Cancel Domain ─────────────────────────────────────
data class CancelDomainDto(
    @field:NotBlank
    val reason: String,
    val cancelDate: String? = null,
)

// ─── Domain Handle ─────────────────────────────────────
data class AddressDto(
    @field:NotBlank val street: String,
    @field:NotBlank val streetNumber: String,
    @field:NotBlank val zipCode: String,
    @field:NotBlank val city: String,
    @field:NotBlank val country: String,
)

data class PhoneDto(
    @field:NotBlank val countryCode: String,
    val areaCode: String? = null,
    @field:NotBlank val subscriberNumber: String,
)

data class BirthInfoDto(
    @field:NotBlank val date: String,
    @field:NotBlank val city: String,
    @field:NotBlank val country: String,
    val zipCode: String? = null,
    val province: String? = null,
)

data class CreateDomainHandleDto(
    val handleType: String, // "person" | "organization"

    @field:NotBlank val firstName: String,
    @field:NotBlank val lastName: String,
    val organization: String? = null,

    @field:NotBlank val email: String,
    val gender: String? = null, // "male" | "female" | "na"

    val birthInfo: BirthInfoDto? = null,

    @field:Valid
    val address: AddressDto,

    @field:Valid
    val phone: PhoneDto,

    val fax: String? = null,
)

data class UpdateDomainHandleDto(
    val handleType: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val organization: String? = null,
    val email: String? = null,
    val gender: String? = null,
    val birthInfo: BirthInfoDto? = null,
    val address: AddressDto? = null,
    val phone: PhoneDto? = null,
    val fax: String? = null,
)

// ─── Update Domain ─────────────────────────────────────
data class HandlesDto(
    val owner: String? = null,
    val admin: String? = null,
    val tech: String? = null,
    val zone: String? = null,
)

data class UpdateDomainDto(
    val nameservers: List<NameserverDto>? = null,
    val handles: HandlesDto? = null,
)

// ─── DNS Record ────────────────────────────────────────
data class CreateDnsRecordDto(
    @field:NotBlank val name: String,
    @field:NotBlank val type: String, // A, AAAA, CNAME, MX, TXT, NS, SRV, CAA
    @field:NotBlank val data: String,
    val ttl: Int? = null,
)
