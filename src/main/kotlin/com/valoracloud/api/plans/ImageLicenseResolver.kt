package com.valoracloud.api.plans

/**
 * Maps OS image selection to billable addon catalog ids and Contabo `license` API values.
 *
 * Supports both legacy plan addons (`image-cpanel`, `image-windows`, …) and the newer
 * license catalog ids (`license-cpanel`, …).
 */
object ImageLicenseResolver {

    const val ADDON_CPANEL = "license-cpanel"
    const val ADDON_PLESK_HOST = "license-plesk-host"
    const val ADDON_PLESK_ADMIN = "license-plesk-admin"
    const val ADDON_PLESK_PRO = "license-plesk-pro"

    const val LEGACY_CPANEL = "image-cpanel"
    const val LEGACY_PLESK_LINUX = "image-plesk-linux"
    const val LEGACY_PLESK_WINDOWS = "image-plesk-windows"
    const val LEGACY_WINDOWS = "image-windows"

    /** Resolve the addon catalog id to bill for this image selection, if any. */
    fun resolveBillableAddonId(
        imageId: String?,
        imageLabel: String?,
        planAddonIds: Set<String>,
    ): String? {
        val id = imageId?.trim().orEmpty()
        if (id.isNotEmpty() && planAddonIds.contains(id)) {
            return id
        }

        resolveLegacyPanelAddonId(imageLabel, planAddonIds)?.let { return it }
        resolveWindowsImageAddonId(imageLabel, planAddonIds)?.let { return it }

        val licenseId = resolveLicenseAddonId(imageLabel ?: id) ?: return null
        if (planAddonIds.contains(licenseId)) return licenseId
        return licenseId
    }

    /** Legacy panel addons configured directly on the plan. */
    fun resolveLegacyPanelAddonId(imageLabel: String?, planAddonIds: Set<String>): String? {
        val label = imageLabel?.lowercase()?.trim().orEmpty()
        if (label.isEmpty()) return null
        val isWindows = label.contains("windows")
        return when {
            label.contains("cpanel") && planAddonIds.contains(LEGACY_CPANEL) -> LEGACY_CPANEL
            label.contains("plesk") && isWindows && planAddonIds.contains(LEGACY_PLESK_WINDOWS) ->
                LEGACY_PLESK_WINDOWS
            label.contains("plesk") && !isWindows && planAddonIds.contains(LEGACY_PLESK_LINUX) ->
                LEGACY_PLESK_LINUX
            else -> null
        }
    }

    /** Windows Server images → versioned or generic Windows addon on the plan. */
    fun resolveWindowsImageAddonId(imageLabel: String?, planAddonIds: Set<String>): String? {
        val label = imageLabel?.lowercase()?.trim().orEmpty()
        if (!label.contains("windows")) return null

        Regex("windows-server-(\\d{4})").find(label)?.groupValues?.getOrNull(1)?.let { year ->
            val versioned = "image-windows-$year"
            if (planAddonIds.contains(versioned)) return versioned
        }

        if (planAddonIds.contains(LEGACY_WINDOWS)) return LEGACY_WINDOWS
        return null
    }

    /** Linux panel images → license addon catalog id. */
    fun resolveLicenseAddonId(imageLabel: String?): String? {
        val label = imageLabel?.lowercase()?.trim().orEmpty()
        if (label.isEmpty()) return null
        return when {
            label.contains("cpanel") -> ADDON_CPANEL
            label.contains("plesk-pro") || label.endsWith("-plesk-pro") -> ADDON_PLESK_PRO
            label.contains("plesk-admin") -> ADDON_PLESK_ADMIN
            label.contains("plesk") -> ADDON_PLESK_HOST
            else -> null
        }
    }

    /** Contabo create-instance `license` enum from addon catalog contaboValue. */
    fun isLicenseAddon(addonId: String): Boolean =
        addonId == ADDON_CPANEL ||
            addonId == ADDON_PLESK_HOST ||
            addonId == ADDON_PLESK_ADMIN ||
            addonId == ADDON_PLESK_PRO

    /** Map billed addon id to Contabo `license` field (legacy image addons included). */
    fun resolveContaboLicenseValue(
        addonId: String?,
        catalogById: Map<String, com.valoracloud.api.entity.AddonCatalog>,
    ): String? {
        if (addonId.isNullOrBlank()) return null
        if (isLicenseAddon(addonId)) {
            return catalogById[addonId]?.contaboValue?.takeIf { it.isNotBlank() }
        }
        return when (addonId) {
            LEGACY_CPANEL -> "cPanel30"
            LEGACY_PLESK_LINUX, LEGACY_PLESK_WINDOWS -> "PleskHost"
            else -> null
        }
    }
}
