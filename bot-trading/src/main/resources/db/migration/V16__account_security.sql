ALTER TABLE tenant
    ADD COLUMN IF NOT EXISTS deletion_requested_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS purge_after TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS billing_country VARCHAR(64);

ALTER TABLE tenant_limits
    ADD COLUMN IF NOT EXISTS max_daily_drawdown_pct NUMERIC(10,4) DEFAULT 0 NOT NULL,
    ADD COLUMN IF NOT EXISTS max_concurrent_positions INT DEFAULT 0 NOT NULL,
    ADD COLUMN IF NOT EXISTS canary_pct NUMERIC(10,4) DEFAULT 0 NOT NULL;

ALTER TABLE tenant_settings
    ADD COLUMN IF NOT EXISTS trading_paused BOOLEAN DEFAULT FALSE NOT NULL;

ALTER TABLE terms_acceptance
    ADD COLUMN IF NOT EXISTS terms_version_hash VARCHAR(128),
    ADD COLUMN IF NOT EXISTS risk_version_hash VARCHAR(128),
    ADD COLUMN IF NOT EXISTS consented_at TIMESTAMP;

UPDATE terms_acceptance
SET
    terms_version_hash = COALESCE(terms_version_hash, version),
    risk_version_hash = COALESCE(risk_version_hash, version),
    consented_at = COALESCE(consented_at, accepted_at);

ALTER TABLE terms_acceptance
    ALTER COLUMN terms_version_hash SET NOT NULL,
    ALTER COLUMN risk_version_hash SET NOT NULL,
    ALTER COLUMN consented_at SET NOT NULL;

ALTER TABLE terms_acceptance
    DROP COLUMN IF EXISTS version,
    DROP COLUMN IF EXISTS accepted_at;

ALTER TABLE tenant_billing
    RENAME COLUMN status TO provider_status;

ALTER TABLE tenant_billing
    ADD COLUMN IF NOT EXISTS billing_state VARCHAR(32) DEFAULT 'ACTIVE' NOT NULL,
    ADD COLUMN IF NOT EXISTS grace_until TIMESTAMP;

CREATE TABLE IF NOT EXISTS tenant_export_token (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    token VARCHAR(128) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    downloaded_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS billing_webhook_event (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    event_id VARCHAR(128) NOT NULL UNIQUE,
    signature VARCHAR(256) NOT NULL,
    payload_json TEXT NOT NULL,
    processed_at TIMESTAMP NOT NULL,
    type VARCHAR(32) NOT NULL
);
