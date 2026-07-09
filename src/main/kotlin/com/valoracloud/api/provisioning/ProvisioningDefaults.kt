package com.valoracloud.api.provisioning

/**
 * Single source of truth for the server login user.
 *
 * BUSINESS RULE — DO NOT CHANGE without explicit owner approval:
 * Linux servers are ALWAYS `root`; only Windows uses `administrator`.
 *
 * A violation locks customers out of their servers (real incident 2026-07-08:
 * a Linux server was provisioned with user `admin` while the storefront told
 * the customer to log in as root — the customer churned). Contabo accepts
 * "admin" | "root" for Linux and "admin" | "administrator" for Windows.
 *
 * Every call site (order creation, Contabo defaultUser, cloud-init chpasswd,
 * post-provision SSH) MUST use these members instead of string literals —
 * ProvisioningUserRuleTest enforces this.
 */
object ProvisioningDefaults {
    const val LINUX_USER = "root"
    const val WINDOWS_USER = "administrator"

    fun sshUserFor(isWindows: Boolean): String = if (isWindows) WINDOWS_USER else LINUX_USER

    /** Windows detection from an image name/id and optional Contabo osType. */
    fun isWindows(imageNameOrId: String?, osType: String? = null): Boolean =
        imageNameOrId?.contains("windows", ignoreCase = true) == true ||
            osType?.equals("Windows", ignoreCase = true) == true
}
