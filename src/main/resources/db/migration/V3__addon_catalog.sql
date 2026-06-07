CREATE TABLE addon_catalog (
    id           VARCHAR(50)  PRIMARY KEY,
    category     VARCHAR(30)  NOT NULL,
    label        VARCHAR(255) NOT NULL,
    contabo_value VARCHAR(100),
    billing_type VARCHAR(30)  NOT NULL DEFAULT 'monthly_recurring',
    is_default   BOOLEAN      NOT NULL DEFAULT false,
    sort_order   INTEGER      NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Regions (9)
INSERT INTO addon_catalog VALUES
  ('region-eu',         'region', 'European Union',           'EU',         'monthly_recurring', true,  10,  NOW(), NOW()),
  ('region-us-east',    'region', 'United States (East)',     'US-east',    'monthly_recurring', false, 20,  NOW(), NOW()),
  ('region-us-central', 'region', 'United States (Central)',  'US-central', 'monthly_recurring', false, 30,  NOW(), NOW()),
  ('region-us-west',    'region', 'United States (West)',     'US-west',    'monthly_recurring', false, 40,  NOW(), NOW()),
  ('region-uk',         'region', 'United Kingdom',           'UK',         'monthly_recurring', false, 50,  NOW(), NOW()),
  ('region-asia-sin',   'region', 'Asia (Singapore)',         'SIN',        'monthly_recurring', false, 60,  NOW(), NOW()),
  ('region-asia-jpn',   'region', 'Asia (Japan)',             'JPN',        'monthly_recurring', false, 70,  NOW(), NOW()),
  ('region-asia-ind',   'region', 'Asia (India)',             'IND',        'monthly_recurring', false, 80,  NOW(), NOW()),
  ('region-aus',        'region', 'Australia (Sydney)',       'AUS',        'monthly_recurring', false, 90,  NOW(), NOW());

-- Storage (4)
INSERT INTO addon_catalog VALUES
  ('storage-ssd-base',     'storage', 'SSD (included)',  NULL, 'monthly_recurring', true,  100, NOW(), NOW()),
  ('storage-ssd-upgrade',  'storage', 'SSD (double)',    NULL, 'monthly_recurring', false, 110, NOW(), NOW()),
  ('storage-nvme-base',    'storage', 'NVMe (included)', NULL, 'monthly_recurring', false, 120, NOW(), NOW()),
  ('storage-nvme-upgrade', 'storage', 'NVMe (double)',   NULL, 'monthly_recurring', false, 130, NOW(), NOW());

-- Windows images (3)
INSERT INTO addon_catalog VALUES
  ('image-windows-2016', 'image', 'Windows Server 2016', 'windows-2016', 'monthly_recurring', false, 200, NOW(), NOW()),
  ('image-windows-2019', 'image', 'Windows Server 2019', 'windows-2019', 'monthly_recurring', false, 210, NOW(), NOW()),
  ('image-windows-2022', 'image', 'Windows Server 2022', 'windows-2022', 'monthly_recurring', false, 220, NOW(), NOW());

-- Backup (2)
INSERT INTO addon_catalog VALUES
  ('backup-none', 'backup', 'No Data Protection', NULL,          'monthly_recurring', true,  300, NOW(), NOW()),
  ('backup-auto', 'backup', 'Auto Backup',        'auto-backup', 'monthly_recurring', false, 310, NOW(), NOW());

-- Networking (2)
INSERT INTO addon_catalog VALUES
  ('networking-none',    'networking', 'No Private Networking', NULL,                 'monthly_recurring', true,  400, NOW(), NOW()),
  ('networking-private', 'networking', 'Private Networking',    'private-networking', 'monthly_recurring', false, 410, NOW(), NOW());

-- Object Storage (5)
INSERT INTO addon_catalog VALUES
  ('objstorage-none',  'object_storage', 'None',                  NULL,   'monthly_recurring', true,  500, NOW(), NOW()),
  ('objstorage-250gb', 'object_storage', '250 GB Object Storage', '250gb','monthly_recurring', false, 510, NOW(), NOW()),
  ('objstorage-500gb', 'object_storage', '500 GB Object Storage', '500gb','monthly_recurring', false, 520, NOW(), NOW()),
  ('objstorage-750gb', 'object_storage', '750 GB Object Storage', '750gb','monthly_recurring', false, 530, NOW(), NOW()),
  ('objstorage-1tb',   'object_storage', '1 TB Object Storage',   '1tb',  'monthly_recurring', false, 540, NOW(), NOW());

-- Monitoring (2)
INSERT INTO addon_catalog VALUES
  ('monitoring-none',    'monitoring', 'No Monitoring', NULL,        'monthly_recurring', true,  600, NOW(), NOW()),
  ('monitoring-enabled', 'monitoring', 'Monitoring',    'monitoring','monthly_recurring', false, 610, NOW(), NOW());
