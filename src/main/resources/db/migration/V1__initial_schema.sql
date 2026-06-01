-- V1__initial_schema.sql — Valora Cloud initial database schema
-- Generated from Prisma schema, adapted for JPA entities

-- Enums as varchar columns (JPA @Enumerated(EnumType.STRING))
-- Roles are handled as text columns with CHECK constraints

-- ─── Users ───────────────────────────────────────────────
CREATE TABLE users (
    id VARCHAR(25) PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'CLIENT' CHECK (role IN ('CLIENT', 'ADMIN')),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'BANNED')),
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    language VARCHAR(5) NOT NULL DEFAULT 'en',
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email) WHERE deleted_at IS NULL;

CREATE TABLE refresh_tokens (
    id VARCHAR(25) PRIMARY KEY,
    token TEXT NOT NULL UNIQUE,
    user_id VARCHAR(25) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);

-- ─── Plans ───────────────────────────────────────────────
CREATE TABLE plans (
    id VARCHAR(25) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL UNIQUE,
    product_type VARCHAR(30) NOT NULL CHECK (product_type IN ('CLOUD_VPS', 'CLOUD_VDS', 'WINDOWS_VPS', 'OBJECT_STORAGE', 'DOMAIN')),
    description TEXT,
    cpu INTEGER NOT NULL DEFAULT 1,
    ram INTEGER NOT NULL DEFAULT 1,
    disk INTEGER NOT NULL DEFAULT 25,
    disk_type VARCHAR(10) NOT NULL DEFAULT 'NVMe',
    bandwidth INTEGER NOT NULL DEFAULT 1,
    port_speed INTEGER,
    snapshots INTEGER NOT NULL DEFAULT 0,
    price1_month NUMERIC(10,2) NOT NULL DEFAULT 0,
    price6_months NUMERIC(10,2) NOT NULL DEFAULT 0,
    price12_months NUMERIC(10,2) NOT NULL DEFAULT 0,
    setup1_month NUMERIC(10,2) NOT NULL DEFAULT 0,
    setup6_months NUMERIC(10,2) NOT NULL DEFAULT 0,
    setup12_months NUMERIC(10,2) NOT NULL DEFAULT 0,
    price_monthly NUMERIC(10,2) NOT NULL DEFAULT 0,
    contabo_plan_id VARCHAR(255) NOT NULL,
    contabo_cost_price NUMERIC(10,2),
    regions JSONB NOT NULL DEFAULT '[]',
    available_addons JSONB NOT NULL DEFAULT '[]',
    storage_tb DOUBLE PRECISION,
    sort_order INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'HIDDEN', 'ARCHIVED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_plans_product_type ON plans(product_type);

-- ─── Orders ──────────────────────────────────────────────
CREATE TABLE orders (
    id VARCHAR(25) PRIMARY KEY,
    user_id VARCHAR(25) NOT NULL REFERENCES users(id),
    plan_id VARCHAR(25) REFERENCES plans(id),
    service_type VARCHAR(20) NOT NULL DEFAULT 'COMPUTE' CHECK (service_type IN ('COMPUTE', 'OBJECT_STORAGE', 'DOMAIN')),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_PAYMENT' CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'PROVISIONING', 'ACTIVE', 'FAILED', 'CANCELLED')),
    payment_method VARCHAR(10) NOT NULL DEFAULT 'STRIPE' CHECK (payment_method IN ('STRIPE', 'CRYPTO')),
    stripe_payment_id VARCHAR(255),
    crypto_payment_id VARCHAR(255),
    crypto_currency VARCHAR(20),
    billing_cycle INTEGER NOT NULL DEFAULT 1,
    base_price NUMERIC(10,2) NOT NULL DEFAULT 0,
    addons_price NUMERIC(10,2) NOT NULL DEFAULT 0,
    setup_fee NUMERIC(10,2) NOT NULL DEFAULT 0,
    total_amount NUMERIC(10,2) NOT NULL DEFAULT 0,
    region VARCHAR(50) NOT NULL DEFAULT 'EU',
    os VARCHAR(50) NOT NULL DEFAULT 'ubuntu-24.04',
    addons JSONB NOT NULL DEFAULT '[]',
    ssh_user VARCHAR(50) NOT NULL DEFAULT 'root',
    hostname VARCHAR(255),
    root_password TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_user ON orders(user_id);
CREATE INDEX idx_orders_status ON orders(status);

-- ─── Servers ─────────────────────────────────────────────
CREATE TABLE servers (
    id VARCHAR(25) PRIMARY KEY,
    user_id VARCHAR(25) NOT NULL REFERENCES users(id),
    order_id VARCHAR(25) NOT NULL UNIQUE REFERENCES orders(id),
    contabo_instance_id VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'PROVISIONING' CHECK (status IN ('PROVISIONING', 'RUNNING', 'STOPPED', 'SUSPENDED', 'REINSTALLING', 'NEEDS_PROVISION', 'ERROR')),
    hostname VARCHAR(255) NOT NULL,
    ip_address VARCHAR(45),
    ssh_user VARCHAR(50) NOT NULL DEFAULT 'root',
    root_password TEXT NOT NULL,
    os VARCHAR(50) NOT NULL,
    region VARCHAR(50) NOT NULL DEFAULT 'EU',
    contabo_data JSONB,
    provisioned_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_servers_user ON servers(user_id);
CREATE INDEX idx_servers_status ON servers(status);

CREATE TABLE provisioning_logs (
    id VARCHAR(25) PRIMARY KEY,
    server_id VARCHAR(25) NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
    step VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_prov_logs_server ON provisioning_logs(server_id);

-- ─── Invoices ────────────────────────────────────────────
CREATE TABLE invoices (
    id VARCHAR(25) PRIMARY KEY,
    user_id VARCHAR(25) NOT NULL REFERENCES users(id),
    order_id VARCHAR(25) NOT NULL REFERENCES orders(id),
    amount NUMERIC(10,2) NOT NULL DEFAULT 0,
    currency VARCHAR(5) NOT NULL DEFAULT 'USD',
    payment_method VARCHAR(10) NOT NULL DEFAULT 'STRIPE' CHECK (payment_method IN ('STRIPE', 'CRYPTO')),
    stripe_invoice_id VARCHAR(255),
    crypto_tx_id VARCHAR(255),
    crypto_currency VARCHAR(20),
    crypto_amount VARCHAR(100),
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_invoices_user ON invoices(user_id);
CREATE INDEX idx_invoices_order ON invoices(order_id);

CREATE TABLE webhook_events (
    id VARCHAR(25) PRIMARY KEY,
    stripe_event_id VARCHAR(255) UNIQUE,
    external_id VARCHAR(255),
    event_source VARCHAR(20) NOT NULL DEFAULT 'stripe',
    event_type VARCHAR(255) NOT NULL,
    processed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_webhook_external ON webhook_events(external_id);

-- ─── Object Storage ──────────────────────────────────────
CREATE TABLE object_storages (
    id VARCHAR(25) PRIMARY KEY,
    user_id VARCHAR(25) NOT NULL REFERENCES users(id),
    order_id VARCHAR(25) NOT NULL UNIQUE REFERENCES orders(id),
    contabo_storage_id VARCHAR(255) UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'PROVISIONING' CHECK (status IN ('PROVISIONING', 'READY', 'UPGRADING', 'ERROR', 'CANCELLED')),
    display_name VARCHAR(255),
    region VARCHAR(50) NOT NULL DEFAULT 'EU',
    total_purchased_space_tb DOUBLE PRECISION NOT NULL DEFAULT 0.25,
    used_space_tb DOUBLE PRECISION DEFAULT 0,
    auto_scaling JSONB,
    s3_endpoint VARCHAR(255),
    s3_access_key TEXT,
    s3_secret_key TEXT,
    provisioned_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ostorage_user ON object_storages(user_id);
CREATE INDEX idx_ostorage_status ON object_storages(status);

-- ─── Domains ─────────────────────────────────────────────
CREATE TABLE domain_handles (
    id VARCHAR(25) PRIMARY KEY,
    user_id VARCHAR(25) NOT NULL REFERENCES users(id),
    contabo_handle_id VARCHAR(255),
    handle_type VARCHAR(20) NOT NULL,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    organization VARCHAR(255),
    email VARCHAR(255) NOT NULL,
    gender VARCHAR(10),
    birth_info JSONB,
    address JSONB NOT NULL DEFAULT '{}',
    phone JSONB NOT NULL DEFAULT '{}',
    fax VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_domain_handles_user ON domain_handles(user_id);

CREATE TABLE domain_tld_pricings (
    id VARCHAR(25) PRIMARY KEY,
    tld VARCHAR(50) NOT NULL UNIQUE,
    registration_price NUMERIC(10,2) NOT NULL DEFAULT 0,
    renewal_price NUMERIC(10,2) NOT NULL DEFAULT 0,
    transfer_price NUMERIC(10,2) NOT NULL DEFAULT 0,
    available BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE domains (
    id VARCHAR(25) PRIMARY KEY,
    user_id VARCHAR(25) NOT NULL REFERENCES users(id),
    order_id VARCHAR(25) NOT NULL UNIQUE REFERENCES orders(id),
    tld_pricing_id VARCHAR(25) NOT NULL REFERENCES domain_tld_pricings(id),
    domain_name VARCHAR(255) NOT NULL UNIQUE,
    auth_code TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACTIVE', 'EXPIRED', 'CANCELLED', 'TRANSFER_IN_PROGRESS', 'TRANSFER_FAILED')),
    owner_handle_id VARCHAR(25) NOT NULL REFERENCES domain_handles(id),
    admin_handle_id VARCHAR(25) NOT NULL REFERENCES domain_handles(id),
    tech_handle_id VARCHAR(25) NOT NULL REFERENCES domain_handles(id),
    zone_handle_id VARCHAR(25) NOT NULL REFERENCES domain_handles(id),
    nameservers JSONB NOT NULL DEFAULT '[]',
    auto_renew BOOLEAN NOT NULL DEFAULT TRUE,
    whois_privacy BOOLEAN NOT NULL DEFAULT FALSE,
    registered_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_domains_user ON domains(user_id);
CREATE INDEX idx_domains_status ON domains(status);
CREATE INDEX idx_domains_name ON domains(domain_name);

-- ─── Snapshots ───────────────────────────────────────────
CREATE TABLE snapshots (
    id VARCHAR(25) PRIMARY KEY,
    server_id VARCHAR(25) NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
    contabo_snapshot_id VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_snapshots_server ON snapshots(server_id);
CREATE INDEX idx_snapshots_contabo ON snapshots(contabo_snapshot_id);

-- ─── Private Networks ────────────────────────────────────
CREATE TABLE private_networks (
    id VARCHAR(25) PRIMARY KEY,
    user_id VARCHAR(25) NOT NULL REFERENCES users(id),
    contabo_network_id VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    region VARCHAR(50) NOT NULL DEFAULT 'EU',
    data_center VARCHAR(100),
    cidr VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pnetworks_user ON private_networks(user_id);
CREATE INDEX idx_pnetworks_contabo ON private_networks(contabo_network_id);

CREATE TABLE private_network_assignments (
    id VARCHAR(25) PRIMARY KEY,
    private_network_id VARCHAR(25) NOT NULL REFERENCES private_networks(id) ON DELETE CASCADE,
    server_id VARCHAR(25) NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
    ip_address VARCHAR(45),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(private_network_id, server_id)
);

CREATE INDEX idx_pna_network ON private_network_assignments(private_network_id);
CREATE INDEX idx_pna_server ON private_network_assignments(server_id);

-- ─── Firewalls ───────────────────────────────────────────
CREATE TABLE firewalls (
    id VARCHAR(25) PRIMARY KEY,
    user_id VARCHAR(25) NOT NULL REFERENCES users(id),
    contabo_firewall_id VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_firewalls_user ON firewalls(user_id);
CREATE INDEX idx_firewalls_contabo ON firewalls(contabo_firewall_id);

CREATE TABLE firewall_rules (
    id VARCHAR(25) PRIMARY KEY,
    firewall_id VARCHAR(25) NOT NULL REFERENCES firewalls(id) ON DELETE CASCADE,
    protocol VARCHAR(10) NOT NULL,
    port INTEGER,
    port_range VARCHAR(20),
    source_ip VARCHAR(45),
    source_net VARCHAR(50),
    action VARCHAR(10) NOT NULL DEFAULT 'allow',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_firewall_rules_fw ON firewall_rules(firewall_id);

CREATE TABLE firewall_assignments (
    id VARCHAR(25) PRIMARY KEY,
    firewall_id VARCHAR(25) NOT NULL REFERENCES firewalls(id) ON DELETE CASCADE,
    server_id VARCHAR(25) NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(firewall_id, server_id)
);

CREATE INDEX idx_fw_assign_fw ON firewall_assignments(firewall_id);
CREATE INDEX idx_fw_assign_server ON firewall_assignments(server_id);

-- ─── VIPs ────────────────────────────────────────────────
CREATE TABLE vips (
    id VARCHAR(25) PRIMARY KEY,
    user_id VARCHAR(25) NOT NULL REFERENCES users(id),
    ip VARCHAR(45) NOT NULL UNIQUE,
    resource_id VARCHAR(255),
    resource_type VARCHAR(50),
    ip_version VARCHAR(5) NOT NULL DEFAULT 'v4',
    type VARCHAR(20) NOT NULL,
    data_center VARCHAR(100),
    region VARCHAR(50) NOT NULL DEFAULT 'EU',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_vips_user ON vips(user_id);
CREATE INDEX idx_vips_ip ON vips(ip);

-- ─── Secrets & Tags ──────────────────────────────────────
CREATE TABLE secrets (
    id VARCHAR(25) PRIMARY KEY,
    user_id VARCHAR(25) NOT NULL REFERENCES users(id),
    contabo_id INTEGER NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_secrets_user ON secrets(user_id);

CREATE TABLE tags (
    id VARCHAR(25) PRIMARY KEY,
    user_id VARCHAR(25) NOT NULL REFERENCES users(id),
    contabo_id INTEGER NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    color VARCHAR(10),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tags_user ON tags(user_id);

-- ─── Monitoring ──────────────────────────────────────────
CREATE TABLE server_monitors (
    id VARCHAR(25) PRIMARY KEY,
    server_id VARCHAR(25) NOT NULL UNIQUE REFERENCES servers(id) ON DELETE CASCADE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    protocol VARCHAR(10) NOT NULL DEFAULT 'https',
    check_url TEXT,
    check_port INTEGER NOT NULL DEFAULT 80,
    check_interval_seconds INTEGER NOT NULL DEFAULT 60,
    agent_port INTEGER,
    agent_secret TEXT,
    alert_threshold_ping_ms INTEGER NOT NULL DEFAULT 200,
    alert_threshold_cpu_pct INTEGER NOT NULL DEFAULT 85,
    alert_threshold_ram_pct INTEGER NOT NULL DEFAULT 85,
    alert_threshold_disk_pct INTEGER NOT NULL DEFAULT 90,
    notify_email VARCHAR(255),
    notify_telegram_chat_id VARCHAR(100),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_monitors_active ON server_monitors(is_active);

CREATE TABLE server_checks (
    id VARCHAR(25) PRIMARY KEY,
    monitor_id VARCHAR(25) NOT NULL REFERENCES server_monitors(id) ON DELETE CASCADE,
    checked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status VARCHAR(15) NOT NULL DEFAULT 'UP' CHECK (status IN ('UP', 'DOWN', 'DEGRADED', 'TIMEOUT')),
    ping_ms INTEGER,
    http_status_code INTEGER,
    http_response_time_ms INTEGER,
    http_response_body_snippet TEXT,
    tcp_open BOOLEAN,
    ssl_valid BOOLEAN,
    ssl_days_until_expiry INTEGER,
    ssl_issuer VARCHAR(500),
    dns_resolved BOOLEAN,
    dns_ip_resolved VARCHAR(45),
    cpu_percent NUMERIC(5,2),
    ram_percent NUMERIC(5,2),
    ram_used_mb INTEGER,
    ram_total_mb INTEGER,
    disk_percent NUMERIC(5,2),
    disk_used_gb NUMERIC(10,2),
    disk_total_gb NUMERIC(10,2),
    load_avg1m NUMERIC(5,2),
    load_avg5m NUMERIC(5,2),
    load_avg15m NUMERIC(5,2),
    network_in_mbps NUMERIC(10,2),
    network_out_mbps NUMERIC(10,2),
    open_connections INTEGER,
    processes_count INTEGER,
    error_message TEXT,
    check_duration_ms INTEGER,
    checker_node VARCHAR(50) NOT NULL DEFAULT 'primary'
);

CREATE INDEX idx_checks_monitor_time ON server_checks(monitor_id, checked_at DESC);
CREATE INDEX idx_checks_status_time ON server_checks(status, checked_at DESC);

CREATE TABLE monitor_alerts (
    id VARCHAR(25) PRIMARY KEY,
    monitor_id VARCHAR(25) NOT NULL REFERENCES server_monitors(id) ON DELETE CASCADE,
    check_id VARCHAR(25) REFERENCES server_checks(id) ON DELETE SET NULL,
    type VARCHAR(20) NOT NULL CHECK (type IN ('DOWN', 'DEGRADED', 'HIGH_CPU', 'HIGH_RAM', 'HIGH_DISK', 'HIGH_LATENCY', 'SSL_EXPIRY', 'SSL_INVALID', 'RECOVERY')),
    severity VARCHAR(10) NOT NULL CHECK (severity IN ('CRITICAL', 'WARNING', 'INFO', 'OK')),
    message TEXT NOT NULL,
    value NUMERIC(10,2),
    threshold NUMERIC(10,2),
    triggered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ,
    is_resolved BOOLEAN NOT NULL DEFAULT FALSE,
    notified_email BOOLEAN NOT NULL DEFAULT FALSE,
    notified_telegram BOOLEAN NOT NULL DEFAULT FALSE,
    ack_at TIMESTAMPTZ,
    ack_note TEXT
);

CREATE INDEX idx_alerts_monitor ON monitor_alerts(monitor_id, is_resolved, triggered_at DESC);

CREATE TABLE uptime_dailies (
    id VARCHAR(25) PRIMARY KEY,
    monitor_id VARCHAR(25) NOT NULL REFERENCES server_monitors(id) ON DELETE CASCADE,
    date DATE NOT NULL,
    total_checks INTEGER NOT NULL DEFAULT 0,
    up_checks INTEGER NOT NULL DEFAULT 0,
    down_checks INTEGER NOT NULL DEFAULT 0,
    degraded_checks INTEGER NOT NULL DEFAULT 0,
    uptime_percent NUMERIC(5,2),
    avg_ping_ms NUMERIC(8,2),
    avg_cpu_percent NUMERIC(5,2),
    avg_ram_percent NUMERIC(5,2),
    max_ping_ms INTEGER,
    max_cpu_percent NUMERIC(5,2),
    incidents_count INTEGER NOT NULL DEFAULT 0,
    total_downtime_seconds INTEGER NOT NULL DEFAULT 0,
    first_down_at TIMESTAMPTZ,
    UNIQUE(monitor_id, date)
);

CREATE INDEX idx_uptime_monitor_date ON uptime_dailies(monitor_id, date DESC);

CREATE TABLE maintenance_windows (
    id VARCHAR(25) PRIMARY KEY,
    monitor_id VARCHAR(25) NOT NULL REFERENCES server_monitors(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    suppress_alerts BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(25),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_maint_monitor ON maintenance_windows(monitor_id);

-- ─── Email Logs ──────────────────────────────────────────
CREATE TABLE email_logs (
    id VARCHAR(25) PRIMARY KEY,
    user_id VARCHAR(25),
    "to" VARCHAR(255) NOT NULL,
    template_name VARCHAR(255) NOT NULL,
    subject VARCHAR(500) NOT NULL,
    language VARCHAR(5) NOT NULL DEFAULT 'en',
    status VARCHAR(20) NOT NULL,
    triggered_by VARCHAR(100) NOT NULL,
    variables JSONB NOT NULL DEFAULT '{}',
    rendered_html TEXT NOT NULL,
    error_message TEXT,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_email_logs_user ON email_logs(user_id);
CREATE INDEX idx_email_logs_status ON email_logs(status);
CREATE INDEX idx_email_logs_template ON email_logs(template_name);
CREATE INDEX idx_email_logs_sent ON email_logs(sent_at DESC);
