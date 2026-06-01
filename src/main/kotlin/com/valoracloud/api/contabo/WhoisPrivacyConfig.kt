package com.valoracloud.api.contabo

/**
 * ValoraCloud WHOIS Privacy Protection constants.
 * Ported from NestJS whois-privacy.config.ts.
 */
object WhoisPrivacyConfig {
    /** Redis key used to cache the Contabo handle ID for the privacy contact. */
    const val CACHE_KEY = "whois:privacy:handle_id"

    val CONTACT = ContaboCreateDomainHandleRequest(
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
}
