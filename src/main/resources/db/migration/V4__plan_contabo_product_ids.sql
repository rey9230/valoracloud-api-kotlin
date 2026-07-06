-- V4: Alternate Contabo product IDs per storage type (NVMe / SSD / Storage)
-- Supports both Flyway snake_case (`plans`) and JPA quoted camelCase (`"Plan"`) schemas.

ALTER TABLE plans ADD COLUMN IF NOT EXISTS contabo_plan_id_ssd VARCHAR(255);
ALTER TABLE plans ADD COLUMN IF NOT EXISTS contabo_plan_id_storage VARCHAR(255);
ALTER TABLE plans ADD COLUMN IF NOT EXISTS contabo_plan_id_windows VARCHAR(255);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = current_schema() AND table_name = 'Plan'
    ) THEN
        ALTER TABLE "Plan" ADD COLUMN IF NOT EXISTS "contaboPlanIdSsd" VARCHAR(255);
        ALTER TABLE "Plan" ADD COLUMN IF NOT EXISTS "contaboPlanIdStorage" VARCHAR(255);
        ALTER TABLE "Plan" ADD COLUMN IF NOT EXISTS "contaboPlanIdWindows" VARCHAR(255);
    END IF;
END $$;
