package com.valoracloud.api.provisioning

import com.valoracloud.api.entity.AddonCatalog
import com.valoracloud.api.entity.Plan
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContaboProvisioningResolverTest {

    private fun plan(
        nvme: String = "V94",
        ssd: String? = "V95",
        storage: String? = "V96",
    ) = Plan(
        name = "Test",
        slug = "test",
        contaboPlanId = nvme,
        contaboPlanIdSsd = ssd,
        contaboPlanIdStorage = storage,
    )

    private fun catalog(vararg entries: AddonCatalog): Map<String, AddonCatalog> =
        entries.associateBy { it.id }

    @Test
    fun `resolveProductId picks SSD and Storage variants`() {
        val p = plan()
        assertEquals("V94", ContaboProvisioningResolver.resolveProductId(p, "storage-nvme-base"))
        assertEquals("V95", ContaboProvisioningResolver.resolveProductId(p, "storage-ssd-base"))
        assertEquals("V96", ContaboProvisioningResolver.resolveProductId(p, "storage-only-tier"))
    }

    @Test
    fun `resolveContaboAddOns includes extraStorage for upgrade addons`() {
        val addOns = ContaboProvisioningResolver.resolveContaboAddOns(
            listOf("storage-nvme-upgrade", "backup-auto"),
            catalog(
                AddonCatalog(id = "backup-auto", category = "backup", label = "Backup", contaboValue = "auto-backup"),
            ),
        )
        assertNotNull(addOns)
        assertNotNull(addOns!!.extraStorage)
        assertNotNull(addOns.backup)
    }

    @Test
    fun `resolveObjectStorageBundle maps catalog sizes`() {
        val spec = ContaboProvisioningResolver.resolveObjectStorageBundle(
            listOf("objstorage-500gb"),
            catalog(
                AddonCatalog(
                    id = "objstorage-500gb",
                    category = "object_storage",
                    label = "500 GB",
                    contaboValue = "500gb",
                ),
            ),
        )
        assertNotNull(spec)
        assertEquals(0.5, spec!!.totalPurchasedSpaceTB, 0.001)
    }

    @Test
    fun `isMonitoringEnabled detects paid monitoring addon`() {
        val enabled = ContaboProvisioningResolver.isMonitoringEnabled(
            listOf("monitoring-enabled"),
            catalog(
                AddonCatalog(
                    id = "monitoring-enabled",
                    category = "monitoring",
                    label = "Monitoring",
                    contaboValue = "monitoring",
                ),
            ),
        )
        assertTrue(enabled)
    }

    @Test
    fun `isMonitoringEnabled false when monitoring-none selected`() {
        val enabled = ContaboProvisioningResolver.isMonitoringEnabled(
            listOf("monitoring-none", "monitoring-enabled"),
            catalog(
                AddonCatalog(
                    id = "monitoring-enabled",
                    category = "monitoring",
                    label = "Monitoring",
                    contaboValue = "monitoring",
                ),
            ),
        )
        assertEquals(false, enabled)
    }

    @Test
    fun `parseObjectStorageSizeTb supports catalog values`() {
        assertEquals(0.25, ContaboProvisioningResolver.parseObjectStorageSizeTb("250gb"))
        assertEquals(1.0, ContaboProvisioningResolver.parseObjectStorageSizeTb("1tb"))
        assertNull(ContaboProvisioningResolver.parseObjectStorageSizeTb("none"))
    }
}
