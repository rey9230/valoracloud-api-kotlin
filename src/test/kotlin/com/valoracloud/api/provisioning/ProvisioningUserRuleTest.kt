package com.valoracloud.api.provisioning

import com.valoracloud.api.provisioning.processor.ProvisioningProcessor
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Guards the business rule: Linux servers are ALWAYS provisioned as `root`;
 * only Windows uses `administrator`.
 *
 * Real incident (2026-07-08): a Linux server shipped with user `admin` while
 * the storefront told the customer to log in as root. The customer was locked
 * out and churned. These tests must fail any change that reintroduces that.
 */
class ProvisioningUserRuleTest {

    // ── The rule itself ──────────────────────────────────────────────

    @Test
    fun `linux user is always root`() {
        assertEquals("root", ProvisioningDefaults.LINUX_USER)
        assertEquals("root", ProvisioningDefaults.sshUserFor(isWindows = false))
    }

    @Test
    fun `windows user is always administrator`() {
        assertEquals("administrator", ProvisioningDefaults.WINDOWS_USER)
        assertEquals("administrator", ProvisioningDefaults.sshUserFor(isWindows = true))
    }

    @Test
    fun `linux user is never admin`() {
        assertFalse(ProvisioningDefaults.sshUserFor(isWindows = false).equals("admin", ignoreCase = true))
        assertFalse(ProvisioningDefaults.sshUserFor(isWindows = true).equals("admin", ignoreCase = true))
    }

    // ── Windows detection ────────────────────────────────────────────

    @Test
    fun `linux images resolve to root`() {
        listOf("ubuntu-26.04", "debian-12", "rockylinux-9", "Ubuntu 24.04 LTS").forEach { label ->
            assertEquals("root", ProvisioningDefaults.sshUserFor(ProvisioningDefaults.isWindows(label)), label)
        }
    }

    @Test
    fun `windows images resolve to administrator`() {
        listOf("windows-server-2025-se", "Windows Server 2022", "WINDOWS-2019").forEach { label ->
            assertEquals("administrator", ProvisioningDefaults.sshUserFor(ProvisioningDefaults.isWindows(label)), label)
        }
        // Contabo image UUID with osType Windows
        assertTrue(ProvisioningDefaults.isWindows("f5193fe6-d547-4726-9271-cdb2819833fd", osType = "Windows"))
    }

    @Test
    fun `unknown image ids default to linux root`() {
        // Storefront sends Contabo image UUIDs — with no Windows marker anywhere the safe default is root.
        assertEquals("root", ProvisioningDefaults.sshUserFor(ProvisioningDefaults.isWindows("f5193fe6-d547-4726-9271-cdb2819833fd")))
        assertEquals("root", ProvisioningDefaults.sshUserFor(ProvisioningDefaults.isWindows(null)))
    }

    // ── Cloud-init ───────────────────────────────────────────────────

    @Test
    fun `cloud-init sets the root password for linux by default`() {
        val yaml = ProvisioningProcessor.buildCloudInit("S3cret!Pass")
        assertTrue(yaml.contains("root:S3cret!Pass"), "chpasswd must target root:\n$yaml")
        assertFalse(yaml.contains("admin:"), "cloud-init must never set a password for 'admin':\n$yaml")
    }

    @Test
    fun `cloud-init targets the resolved user for windows`() {
        val yaml = ProvisioningProcessor.buildCloudInit("S3cret!Pass", ProvisioningDefaults.sshUserFor(true))
        assertTrue(yaml.contains("administrator:S3cret!Pass"))
    }

    // ── Tripwire: no hardcoded user literals in provisioning code ────

    @Test
    fun `provisioning code never hardcodes login users`() {
        val files = listOf(
            "src/main/kotlin/com/valoracloud/api/orders/OrdersService.kt",
            "src/main/kotlin/com/valoracloud/api/provisioning/processor/ProvisioningProcessor.kt",
            "src/main/kotlin/com/valoracloud/api/contabo/ContaboService.kt",
        )
        val forbidden = listOf("\"admin\"", "\"administrator\"", "\"root\"")
        files.forEach { path ->
            val file = File(path)
            assertTrue(file.exists(), "Expected source file at $path — update this test if it moved")
            val source = file.readText()
            forbidden.forEach { literal ->
                assertFalse(
                    source.contains(literal),
                    "$path contains a hardcoded $literal — use ProvisioningDefaults instead",
                )
            }
        }
    }
}
