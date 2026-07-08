package com.valoracloud.api.plans

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ImageLicenseResolverTest {

    @Test
    fun `resolveLicenseAddonId maps cpanel and plesk labels`() {
        assertEquals(ImageLicenseResolver.ADDON_CPANEL, ImageLicenseResolver.resolveLicenseAddonId("ubuntu-24.04-cpanel"))
        assertEquals(ImageLicenseResolver.ADDON_PLESK_HOST, ImageLicenseResolver.resolveLicenseAddonId("ubuntu-24.04-plesk"))
        assertEquals(ImageLicenseResolver.ADDON_PLESK_PRO, ImageLicenseResolver.resolveLicenseAddonId("ubuntu-22.04-plesk-pro"))
        assertNull(ImageLicenseResolver.resolveLicenseAddonId("ubuntu-24.04"))
    }

    @Test
    fun `resolveBillableAddonId prefers plan image addon over license`() {
        val planAddonIds = setOf("image-windows-2022")
        assertEquals(
            "image-windows-2022",
            ImageLicenseResolver.resolveBillableAddonId("image-windows-2022", "windows-server-2022-se", planAddonIds),
        )
    }

    @Test
    fun `resolveBillableAddonId uses legacy image-cpanel on plan`() {
        val planAddonIds = setOf("image-cpanel", "region-eu")
        assertEquals(
            ImageLicenseResolver.LEGACY_CPANEL,
            ImageLicenseResolver.resolveBillableAddonId(
                "c0200107-cc26-4776-9775-1942841a473c",
                "ubuntu-24.04-cpanel",
                planAddonIds,
            ),
        )
    }

    @Test
    fun `resolveBillableAddonId maps windows label to legacy image-windows`() {
        val planAddonIds = setOf("image-windows", "region-eu")
        assertEquals(
            ImageLicenseResolver.LEGACY_WINDOWS,
            ImageLicenseResolver.resolveBillableAddonId(
                "uuid-windows-image",
                "windows-server-2022-se",
                planAddonIds,
            ),
        )
    }

    @Test
    fun `resolveBillableAddonId maps linux panel image to license addon when no legacy`() {
        val planAddonIds = setOf("region-eu", "backup-auto")
        assertEquals(
            ImageLicenseResolver.ADDON_CPANEL,
            ImageLicenseResolver.resolveBillableAddonId(
                "c0200107-cc26-4776-9775-1942841a473c",
                "ubuntu-24.04-cpanel",
                planAddonIds,
            ),
        )
    }

    @Test
    fun `resolveContaboLicenseValue maps legacy cpanel addon`() {
        val value = ImageLicenseResolver.resolveContaboLicenseValue(
            ImageLicenseResolver.LEGACY_CPANEL,
            emptyMap(),
        )
        assertEquals("cPanel30", value)
    }
}
