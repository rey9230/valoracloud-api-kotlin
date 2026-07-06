-- V5: Phase 2 — bundled object storage, addon costs, plan margin

-- Allow bundled object storage on same order as VPS (server_id link)
ALTER TABLE object_storages ADD COLUMN IF NOT EXISTS server_id VARCHAR(25);
ALTER TABLE object_storages DROP CONSTRAINT IF EXISTS object_storages_order_id_key;

ALTER TABLE "ObjectStorage" ADD COLUMN IF NOT EXISTS "serverId" VARCHAR(25);
ALTER TABLE "ObjectStorage" DROP CONSTRAINT IF EXISTS "ObjectStorage_orderId_key";

-- Contabo wholesale cost on addon catalog entries (for margin pricing)
ALTER TABLE addon_catalog ADD COLUMN IF NOT EXISTS contabo_cost_price NUMERIC(10,2);

-- Optional per-plan margin override (%)
ALTER TABLE plans ADD COLUMN IF NOT EXISTS margin_percent NUMERIC(5,2);
ALTER TABLE "Plan" ADD COLUMN IF NOT EXISTS "marginPercent" NUMERIC(5,2);

-- Seed addon wholesale costs (USD/mo reference — adjust in admin)
UPDATE addon_catalog SET contabo_cost_price = 0.00 WHERE id = 'region-eu';
UPDATE addon_catalog SET contabo_cost_price = 1.50 WHERE id = 'region-us-east';
UPDATE addon_catalog SET contabo_cost_price = 1.50 WHERE id = 'region-us-central';
UPDATE addon_catalog SET contabo_cost_price = 1.50 WHERE id = 'region-us-west';
UPDATE addon_catalog SET contabo_cost_price = 2.00 WHERE id = 'region-uk';
UPDATE addon_catalog SET contabo_cost_price = 2.00 WHERE id = 'region-asia-sin';
UPDATE addon_catalog SET contabo_cost_price = 2.00 WHERE id = 'region-asia-jpn';
UPDATE addon_catalog SET contabo_cost_price = 2.00 WHERE id = 'region-asia-ind';
UPDATE addon_catalog SET contabo_cost_price = 2.00 WHERE id = 'region-aus';
UPDATE addon_catalog SET contabo_cost_price = 1.00 WHERE id = 'backup-auto';
UPDATE addon_catalog SET contabo_cost_price = 1.50 WHERE id = 'networking-private';
UPDATE addon_catalog SET contabo_cost_price = 0.50 WHERE id = 'monitoring-enabled';
UPDATE addon_catalog SET contabo_cost_price = 2.49 WHERE id = 'objstorage-250gb';
UPDATE addon_catalog SET contabo_cost_price = 4.49 WHERE id = 'objstorage-500gb';
UPDATE addon_catalog SET contabo_cost_price = 6.49 WHERE id = 'objstorage-750gb';
UPDATE addon_catalog SET contabo_cost_price = 8.49 WHERE id = 'objstorage-1tb';
