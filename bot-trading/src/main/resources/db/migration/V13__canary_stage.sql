CREATE TABLE IF NOT EXISTS preset_canary_state (
    preset_id UUID PRIMARY KEY REFERENCES preset_versions(id),
    symbol VARCHAR(40) NOT NULL,
    regime VARCHAR(20),
    status VARCHAR(30) NOT NULL,
    stage_index INTEGER NOT NULL,
    current_multiplier DOUBLE PRECISION NOT NULL,
    oos_pf DOUBLE PRECISION,
    oos_trades INTEGER,
    shadow_pf DOUBLE PRECISION,
    shadow_trades_baseline INTEGER,
    last_shadow_evaluation TIMESTAMPTZ,
    run_id VARCHAR(120),
    notes TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_preset_canary_state_status ON preset_canary_state(status);
