-- Panel license addons (Contabo `license` field — billed separately from base VPS)

INSERT INTO addon_catalog (id, category, label, contabo_value, billing_type, is_default, sort_order, created_at, updated_at)
VALUES
  ('license-cpanel', 'license', 'cPanel License', 'cPanel30', 'monthly_recurring', false, 230, NOW(), NOW()),
  ('license-plesk-host', 'license', 'Plesk Host License', 'PleskHost', 'monthly_recurring', false, 231, NOW(), NOW()),
  ('license-plesk-admin', 'license', 'Plesk Admin License', 'PleskAdmin', 'monthly_recurring', false, 232, NOW(), NOW()),
  ('license-plesk-pro', 'license', 'Plesk Pro License', 'PleskPro', 'monthly_recurring', false, 233, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Wholesale reference (USD/mo) — adjust in admin; retail = cost + plan margin
UPDATE addon_catalog SET contabo_cost_price = 14.00 WHERE id = 'license-cpanel';
UPDATE addon_catalog SET contabo_cost_price = 11.00 WHERE id = 'license-plesk-host';
UPDATE addon_catalog SET contabo_cost_price = 15.00 WHERE id = 'license-plesk-admin';
UPDATE addon_catalog SET contabo_cost_price = 18.00 WHERE id = 'license-plesk-pro';
