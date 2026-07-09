package com.valoracloud.api.provisioning

import com.valoracloud.api.provisioning.processor.ProvisioningProcessor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Guards the white-label rule: the customer must NEVER see the upstream
 * provider (Contabo) — not in the MOTD, the banner, the hostname, nor the
 * SSH login banner.
 *
 * Real incident (2026-07-08): a customer logged into a server that still
 * showed "This server is hosted by Contabo" plus a vmiXXXX hostname,
 * discovered the reseller relationship, and churned.
 */
class ProvisioningBrandingTest {

    private val commands = ProvisioningProcessor.buildPostProvisionCommands(
        hostname = "srv-abc12345.valoracloud.com",
        regionLabel = "eu-de-1 · Nuremberg, Germany",
        bannerContent = ValoraBranding.bannerContent(),
        motdScript = ValoraBranding.motdScript(),
    )

    // ── Valora branding content ──────────────────────────────────────

    @Test
    fun `banner and motd never mention the upstream provider`() {
        listOf(ValoraBranding.bannerContent(), ValoraBranding.motdScript()).forEach { content ->
            assertFalse(content.contains("contabo", ignoreCase = true), "Upstream provider leaked:\n$content")
        }
    }

    @Test
    fun `motd points customers to valora support channels`() {
        val motd = ValoraBranding.motdScript()
        assertTrue(motd.contains("valoracloud.com"))
        assertTrue(motd.contains("support@valoracloud.com"))
    }

    // ── De-branding commands ─────────────────────────────────────────

    @Test
    fun `post-provision wipes provider motd, issue and ssh banner`() {
        val joined = commands.joinToString("\n")
        assertTrue(joined.contains(": > /etc/motd"), "must blank /etc/motd")
        assertTrue(joined.contains("/etc/issue"), "must blank /etc/issue")
        assertTrue(joined.contains("rm -f /etc/profile.d/contabo*"), "must remove contabo profile scripts")
        assertTrue(joined.contains("Banner none"), "must disable the SSH banner")
        assertTrue(joined.contains("PrintMotd no"), "must disable sshd PrintMotd")
    }

    @Test
    fun `post-provision disables every motd fragment except valora's`() {
        assertTrue(commands.any { it.contains("update-motd.d/*") && it.contains("00-valora") })
    }

    @Test
    fun `post-provision sets the valora hostname`() {
        assertTrue(commands.any { it.startsWith("hostnamectl set-hostname srv-abc12345.valoracloud.com") })
    }

    @Test
    fun `customer hostname is valora-branded, never contabo's vmi`() {
        val hostname = ProvisioningProcessor.customerHostname("abcdefgh12345", "valoracloud.com")
        assertEquals("srv-abcdefgh.valoracloud.com", hostname)
        assertFalse(hostname.contains("vmi"), "must not leak Contabo's vmi naming")
    }

    // ── Cloud-init ───────────────────────────────────────────────────

    @Test
    fun `cloud-init installs valora files and no provider mention`() {
        val yaml = ProvisioningProcessor.buildCloudInit("S3cret!Pass")
        assertTrue(yaml.contains("/etc/valora/"), "must write valora files")
        assertFalse(yaml.contains("contabo", ignoreCase = true))
        assertTrue(yaml.contains("ssh_pwauth: true"), "password SSH must stay enabled for the customer")
    }
}
