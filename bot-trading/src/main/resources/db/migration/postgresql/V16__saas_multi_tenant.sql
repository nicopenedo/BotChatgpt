-- SaaS multi-tenant schema for non-custodial bots
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS tenant (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name TEXT NOT NULL,
    email_owner TEXT NOT NULL,
    plan TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'pending',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_tenant_email ON tenant (email_owner);

CREATE TABLE IF NOT EXISTS tenant_user (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    email TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    role TEXT NOT NULL,
    mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    mfa_secret TEXT,
    status TEXT NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_tenant_user_email ON tenant_user (tenant_id, email);

CREATE TABLE IF NOT EXISTS tenant_api_key (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    exchange TEXT NOT NULL,
    label TEXT,
    enc_api_key BYTEA NOT NULL,
    enc_secret BYTEA NOT NULL,
    ip_whitelist TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    can_withdraw BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    rotated_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_tenant_api_key_tenant ON tenant_api_key (tenant_id);

CREATE TABLE IF NOT EXISTS tenant_settings (
    tenant_id UUID PRIMARY KEY REFERENCES tenant(id) ON DELETE CASCADE,
    risk_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    router_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    bandit_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    exec_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    throttle_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    notifications_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    feature_flags_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS tenant_limits (
    tenant_id UUID PRIMARY KEY REFERENCES tenant(id) ON DELETE CASCADE,
    max_bots INT NOT NULL,
    max_symbols INT NOT NULL,
    canary_share_max NUMERIC(8,4) NOT NULL,
    max_trades_per_day INT NOT NULL,
    data_retention_days INT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS tenant_billing (
    tenant_id UUID PRIMARY KEY REFERENCES tenant(id) ON DELETE CASCADE,
    provider TEXT NOT NULL,
    customer_id TEXT NOT NULL,
    subscription_id TEXT,
    plan TEXT NOT NULL,
    status TEXT NOT NULL,
    hwm_pnl_net NUMERIC(18,8) NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS audit_event (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    user_id UUID,
    type TEXT NOT NULL,
    payload_json JSONB NOT NULL,
    ts TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_event_tenant_ts ON audit_event (tenant_id, ts DESC);

CREATE TABLE IF NOT EXISTS terms_acceptance (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES tenant_user(id) ON DELETE CASCADE,
    version TEXT NOT NULL,
    accepted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ip TEXT,
    ua TEXT
);

CREATE INDEX IF NOT EXISTS idx_terms_acceptance_tenant ON terms_acceptance (tenant_id, version);

CREATE TABLE IF NOT EXISTS referral (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    referrer_tenant_id UUID REFERENCES tenant(id) ON DELETE SET NULL,
    referred_tenant_id UUID REFERENCES tenant(id) ON DELETE CASCADE,
    reward_state TEXT NOT NULL DEFAULT 'pending',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_referral_referrer ON referral (referrer_tenant_id);

-- audit_event is append only
CREATE OR REPLACE FUNCTION enforce_audit_worm() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_event is append-only';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS audit_event_no_update ON audit_event;
CREATE TRIGGER audit_event_no_update
    BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION enforce_audit_worm();

-- Terms acceptance is immutable
CREATE OR REPLACE FUNCTION enforce_terms_worm() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'terms_acceptance entries are immutable';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS terms_acceptance_no_update ON terms_acceptance;
CREATE TRIGGER terms_acceptance_no_update
    BEFORE UPDATE OR DELETE ON terms_acceptance
    FOR EACH ROW EXECUTE FUNCTION enforce_terms_worm();

-- Row level security configuration
ALTER TABLE tenant ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_user ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_api_key ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_limits ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_billing ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE terms_acceptance ENABLE ROW LEVEL SECURITY;
ALTER TABLE referral ENABLE ROW LEVEL SECURITY;

CREATE OR REPLACE FUNCTION current_tenant_uuid() RETURNS UUID AS $$
BEGIN
    RETURN NULLIF(current_setting('app.current_tenant', true), '')::UUID;
END;
$$ LANGUAGE plpgsql STABLE;

CREATE POLICY tenant_isolation ON tenant
    USING (id = current_tenant_uuid())
    WITH CHECK (id = current_tenant_uuid());

CREATE POLICY tenant_isolation_child ON tenant_user
    USING (tenant_id = current_tenant_uuid())
    WITH CHECK (tenant_id = current_tenant_uuid());

CREATE POLICY tenant_isolation_api_keys ON tenant_api_key
    USING (tenant_id = current_tenant_uuid())
    WITH CHECK (tenant_id = current_tenant_uuid());

CREATE POLICY tenant_isolation_settings ON tenant_settings
    USING (tenant_id = current_tenant_uuid())
    WITH CHECK (tenant_id = current_tenant_uuid());

CREATE POLICY tenant_isolation_limits ON tenant_limits
    USING (tenant_id = current_tenant_uuid())
    WITH CHECK (tenant_id = current_tenant_uuid());

CREATE POLICY tenant_isolation_billing ON tenant_billing
    USING (tenant_id = current_tenant_uuid())
    WITH CHECK (tenant_id = current_tenant_uuid());

CREATE POLICY tenant_isolation_audit ON audit_event
    USING (tenant_id = current_tenant_uuid())
    WITH CHECK (tenant_id = current_tenant_uuid());

CREATE POLICY tenant_isolation_terms ON terms_acceptance
    USING (tenant_id = current_tenant_uuid())
    WITH CHECK (tenant_id = current_tenant_uuid());

CREATE POLICY tenant_isolation_referral ON referral
    USING (referrer_tenant_id = current_tenant_uuid() OR referred_tenant_id = current_tenant_uuid())
    WITH CHECK (referrer_tenant_id = current_tenant_uuid() OR referred_tenant_id = current_tenant_uuid());

-- helper function to clear tenant context
CREATE OR REPLACE FUNCTION reset_current_tenant() RETURNS VOID AS $$
BEGIN
    PERFORM set_config('app.current_tenant', '', true);
END;
$$ LANGUAGE plpgsql;
