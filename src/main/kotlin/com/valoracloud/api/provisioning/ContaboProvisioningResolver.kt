package com.valoracloud.api.provisioning

import com.valoracloud.api.contabo.ContaboCreateInstanceAddOns
import com.valoracloud.api.entity.AddonCatalog
import com.valoracloud.api.entity.Plan
import com.valoracloud.api.plans.ImageLicenseResolver

enum class StorageType {
    NVME,
    SSD,
    STORAGE,
}

data class ObjectStorageBundleSpec(
    val addonId: String,
    val totalPurchasedSpaceTB: Double,
)

object ContaboProvisioningResolver {

    fun resolveStorageType(storageAddonId: String?): StorageType {
        if (storageAddonId == null) return StorageType.NVME
        val id = storageAddonId.lowercase()
        return when {
            id.contains("ssd") -> StorageType.SSD
            id.contains("nvme") -> StorageType.NVME
            id.contains("storage") && id.contains("upgrade") -> StorageType.NVME
            id.contains("storage") -> StorageType.STORAGE
            else -> StorageType.NVME
        }
    }

    fun isStorageUpgradeAddon(addonId: String): Boolean {
        val id = addonId.lowercase()
        return id.startsWith("storage-") && id.contains("upgrade")
    }

    fun resolveProductId(plan: Plan, storageAddonId: String?): String {
        return when (resolveStorageType(storageAddonId)) {
            StorageType.SSD ->
                plan.contaboPlanIdSsd?.takeIf { it.isNotBlank() } ?: plan.contaboPlanId
            StorageType.STORAGE ->
                plan.contaboPlanIdStorage?.takeIf { it.isNotBlank() } ?: plan.contaboPlanId
            StorageType.NVME -> plan.contaboPlanId
        }.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Plan ${plan.id} has no contaboPlanId configured")
    }

    fun resolveRegion(regionAddonId: String, catalogEntry: AddonCatalog?): String {
        val value = catalogEntry?.contaboValue?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Unknown region addon ID: $regionAddonId")
        return value
    }

    fun isMonitoringEnabled(
        addonIds: List<String>,
        catalogById: Map<String, AddonCatalog>,
    ): Boolean {
        if (addonIds.contains("monitoring-none")) return false
        return addonIds.any { id ->
            val entry = catalogById[id] ?: return@any false
            entry.category == "monitoring" &&
                entry.contaboValue?.equals("monitoring", ignoreCase = true) == true
        }
    }

    fun resolveObjectStorageBundle(
        addonIds: List<String>,
        catalogById: Map<String, AddonCatalog>,
    ): ObjectStorageBundleSpec? {
        for (addonId in addonIds) {
            if (addonId == "objstorage-none") continue
            val entry = catalogById[addonId] ?: continue
            if (entry.category != "object_storage") continue
            val tb = parseObjectStorageSizeTb(entry.contaboValue) ?: continue
            return ObjectStorageBundleSpec(addonId = addonId, totalPurchasedSpaceTB = tb)
        }
        return null
    }

    fun parseObjectStorageSizeTb(contaboValue: String?): Double? =
        when (contaboValue?.lowercase()) {
            "250gb" -> 0.25
            "500gb" -> 0.5
            "750gb" -> 0.75
            "1tb" -> 1.0
            else -> null
        }

    /**
     * Maps selected addon ids to Contabo create-instance addOns payload.
     */
    fun resolveContaboAddOns(
        addonIds: List<String>,
        catalogById: Map<String, AddonCatalog>,
    ): ContaboCreateInstanceAddOns? {
        var privateNetworking = false
        var backup = false
        var extraStorage = false

        for (addonId in addonIds) {
            if (isStorageUpgradeAddon(addonId)) {
                extraStorage = true
                continue
            }
            val value = catalogById[addonId]?.contaboValue?.lowercase() ?: continue
            when (value) {
                "private-networking", "privatenetworking" -> privateNetworking = true
                "auto-backup", "backup" -> backup = true
                "extra-storage", "extrastorage" -> extraStorage = true
            }
        }

        if (!privateNetworking && !backup && !extraStorage) return null
        return ContaboCreateInstanceAddOns(
            privateNetworking = if (privateNetworking) emptyMap() else null,
            backup = if (backup) emptyMap() else null,
            extraStorage = if (extraStorage) emptyMap() else null,
        )
    }

    /** Contabo create-instance `license` value from billed addon ids. */
    fun resolveLicense(
        addonIds: List<String>,
        catalogById: Map<String, AddonCatalog>,
    ): String? {
        for (addonId in addonIds) {
            ImageLicenseResolver.resolveContaboLicenseValue(addonId, catalogById)?.let { return it }
        }
        return null
    }
}
