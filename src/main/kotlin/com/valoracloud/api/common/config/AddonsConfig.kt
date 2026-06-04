package com.valoracloud.api.common.config

/**
 * Global metadata for all addon options.
 * Actual prices are per-plan (stored in plan.availableAddons JSON).
 */
enum class AddonCategory(val id: String) {
    REGION("region"),
    STORAGE("storage"),
    IMAGE("image"),
    BACKUP("backup"),
    NETWORKING("networking"),
    OBJECT_STORAGE("object_storage"),
    MONITORING("monitoring"),
}

data class AddonMeta(
    val id: String,
    val category: AddonCategory,
    val label: String,
    val contaboValue: String? = null,
    val default: Boolean = false,
)

data class PlanAddon(
    val id: String,
    val priceMonthly: Double,
    val label: String? = null,
)

// ─── Region Add-ons ─────────────────────────────────────
val REGION_ADDONS = listOf(
    AddonMeta("region-eu", AddonCategory.REGION, "European Union", "EU", true),
    AddonMeta("region-us-east", AddonCategory.REGION, "United States (East)", "US-east"),
    AddonMeta("region-us-central", AddonCategory.REGION, "United States (Central)", "US-central"),
    AddonMeta("region-us-west", AddonCategory.REGION, "United States (West)", "US-west"),
    AddonMeta("region-uk", AddonCategory.REGION, "United Kingdom", "UK"),
    AddonMeta("region-asia-sin", AddonCategory.REGION, "Asia (Singapore)", "SIN"),
    AddonMeta("region-asia-jpn", AddonCategory.REGION, "Asia (Japan)", "JPN"),
    AddonMeta("region-asia-ind", AddonCategory.REGION, "Asia (India)", "IND"),
    AddonMeta("region-aus", AddonCategory.REGION, "Australia (Sydney)", "AUS"),
)

// ─── Storage Type Add-ons ───────────────────────────────
val STORAGE_ADDONS = listOf(
    AddonMeta("storage-ssd-base", AddonCategory.STORAGE, "SSD (included)", default = true),
    AddonMeta("storage-ssd-upgrade", AddonCategory.STORAGE, "SSD (double)"),
    AddonMeta("storage-nvme-base", AddonCategory.STORAGE, "NVMe (included)"),
    AddonMeta("storage-nvme-upgrade", AddonCategory.STORAGE, "NVMe (double)"),
)

// ─── Image / OS Add-ons ────────────────────────────────
val IMAGE_ADDONS = emptyList<AddonMeta>()

// ─── Backup Add-ons ────────────────────────────────────
val BACKUP_ADDONS = listOf(
    AddonMeta("backup-none", AddonCategory.BACKUP, "No Data Protection", default = true),
    AddonMeta("backup-auto", AddonCategory.BACKUP, "Auto Backup", "auto-backup"),
)

// ─── Networking Add-ons ────────────────────────────────
val NETWORKING_ADDONS = listOf(
    AddonMeta("networking-none", AddonCategory.NETWORKING, "No Private Networking", default = true),
    AddonMeta("networking-private", AddonCategory.NETWORKING, "Private Networking", "private-networking"),
)

// ─── Object Storage Add-ons ────────────────────────────
val OBJECT_STORAGE_ADDONS = listOf(
    AddonMeta("objstorage-none", AddonCategory.OBJECT_STORAGE, "None", default = true),
    AddonMeta("objstorage-250gb", AddonCategory.OBJECT_STORAGE, "250 GB Object Storage", "250gb"),
    AddonMeta("objstorage-500gb", AddonCategory.OBJECT_STORAGE, "500 GB Object Storage", "500gb"),
    AddonMeta("objstorage-750gb", AddonCategory.OBJECT_STORAGE, "750 GB Object Storage", "750gb"),
    AddonMeta("objstorage-1tb", AddonCategory.OBJECT_STORAGE, "1 TB Object Storage", "1tb"),
)

// ─── Monitoring Add-on ─────────────────────────────────
val MONITORING_ADDONS = listOf(
    AddonMeta("monitoring-none", AddonCategory.MONITORING, "No Monitoring", default = true),
    AddonMeta("monitoring-enabled", AddonCategory.MONITORING, "Monitoring", "monitoring"),
)

// ─── All Add-ons Combined ──────────────────────────────
val ALL_ADDONS = REGION_ADDONS + STORAGE_ADDONS + IMAGE_ADDONS +
    BACKUP_ADDONS + NETWORKING_ADDONS + OBJECT_STORAGE_ADDONS + MONITORING_ADDONS

val ALLOWED_TERMS = listOf(1, 6, 12)

// ─── Helpers ───────────────────────────────────────────

fun findAddonMeta(addonId: String): AddonMeta? = ALL_ADDONS.find { it.id == addonId }

fun findPlanAddonPrice(planAddons: List<PlanAddon>, addonId: String): Double? =
    planAddons.find { it.id == addonId }?.priceMonthly

fun calculateAddonsCost(planAddons: List<PlanAddon>, selectedAddonIds: List<String>): Double =
    selectedAddonIds.sumOf { id -> findPlanAddonPrice(planAddons, id) ?: 0.0 }