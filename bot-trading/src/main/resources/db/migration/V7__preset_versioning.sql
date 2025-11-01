CREATE TABLE IF NOT EXISTS preset_versions (
    id UUID PRIMARY KEY,
    regime VARCHAR(32) NOT NULL,
    side VARCHAR(16) NOT NULL,
    params_json JSONB,
    signals_json JSONB,
    source_run_id VARCHAR(120),
    status VARCHAR(16) NOT NULL,
    code_sha VARCHAR(128),
    data_hash VARCHAR(128),
    labels_hash VARCHAR(128),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    activated_at TIMESTAMPTZ,
    retired_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS backtest_runs (
    run_id VARCHAR(120) PRIMARY KEY,
    symbol VARCHAR(20),
    interval VARCHAR(20),
    ts_from TIMESTAMPTZ,
    ts_to TIMESTAMPTZ,
    regime_mask VARCHAR(120),
    ga_pop INTEGER,
    ga_gens INTEGER,
    fitness_def VARCHAR(120),
    seed BIGINT,
    oos_metrics_json JSONB,
    per_split_metrics_json JSONB,
    code_sha VARCHAR(128),
    data_hash VARCHAR(128),
    labels_hash VARCHAR(128),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS evaluation_snapshots (
    id UUID PRIMARY KEY,
    preset_id UUID NOT NULL REFERENCES preset_versions(id) ON DELETE CASCADE,
    window VARCHAR(32) NOT NULL,
    oos_metrics_json JSONB,
    shadow_metrics_json JSONB,
    live_metrics_json JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS live_tracking (
    id UUID PRIMARY KEY,
    preset_id UUID NOT NULL REFERENCES preset_versions(id) ON DELETE CASCADE,
    ts_from TIMESTAMPTZ,
    ts_to TIMESTAMPTZ,
    capital_risked NUMERIC,
    pnl NUMERIC,
    pf NUMERIC,
    maxdd NUMERIC,
    trades INTEGER,
    slippage_bps NUMERIC,
    drift_flags JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_preset_versions_regime_side_status
    ON preset_versions(regime, side, status);
CREATE INDEX IF NOT EXISTS idx_preset_versions_created ON preset_versions(created_at);
CREATE INDEX IF NOT EXISTS idx_snapshots_preset ON evaluation_snapshots(preset_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_live_tracking_preset ON live_tracking(preset_id, created_at DESC);
