package com.valoracloud.api.common.config

import com.valoracloud.api.contabo.*

/**
 * ValoraCloud WHOIS Privacy Protection contact data.
 *
 * When a user pays for WHOIS Privacy, the public WHOIS registrant fields
 * are replaced with this organization contact so the user's identity is hidden.
 *
 * This handle is created on-demand in Contabo the first time any domain is
 * provisioned with WHOIS Privacy enabled. Its ID is then cached indefinitely
 * under WHOIS_PRIVACY_CACHE_KEY.
 *
 * It is NEVER exposed to end-users through the API.
 *
 * ⚠️  Update the street address once ValoraCloud has a registered business address on file.
 */
val WHOIS_PRIVACY_CONTACT = ContaboCreateDomainHandleRequest(
    handleType = HandleType.ORGANIZATION,
    firstName = "Privacy",
    lastName = "Services",
    organization = "Valora Cloud LLC",
    email = "privacy@valoracloud.com",
    address = ContaboHandleAddress(
        street = "Mckee Lake Drive North",
        streetNumber = "5957",
        zipCode = "33709",
        city = "Saint Petersburg",
        country = "US",
    ),
    phone = ContaboHandlePhone(
        countryCode = "+1",
        areaCode = "727",
        subscriberNumber = "9067761",
    ),
)

/** Redis key used to cache the Contabo handle ID for the privacy contact. */
const val WHOIS_PRIVACY_CACHE_KEY = "whois:privacy:handle_id"
