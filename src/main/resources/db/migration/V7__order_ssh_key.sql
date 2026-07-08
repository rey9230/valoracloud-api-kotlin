-- V7: Persist selected SSH key on checkout orders

ALTER TABLE orders ADD COLUMN IF NOT EXISTS ssh_key_id VARCHAR(25);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = current_schema() AND table_name = 'Order'
    ) THEN
        ALTER TABLE "Order" ADD COLUMN IF NOT EXISTS "sshKeyId" VARCHAR(25);
    END IF;
END $$;
