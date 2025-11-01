CREATE TABLE IF NOT EXISTS bandit_arm (
    id UUID PRIMARY KEY,
    symbol TEXT NOT NULL,
    regime TEXT NOT NULL,
    side TEXT NOT NULL,
    preset_id UUID NOT NULL,
    status TEXT NOT NULL,
    role TEXT NOT NULL,
    stats_json JSONB NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_bandit_arm_lookup ON bandit_arm(symbol, regime, side);

CREATE TABLE IF NOT EXISTS bandit_pull (
    id BIGSERIAL PRIMARY KEY,
    arm_id UUID REFERENCES bandit_arm(id) ON DELETE CASCADE,
    ts TIMESTAMPTZ NOT NULL,
    decision_id TEXT,
    context_json JSONB,
    reward DOUBLE PRECISION,
    pnl_r DOUBLE PRECISION,
    slippage_bps DOUBLE PRECISION,
    fees_bps DOUBLE PRECISION,
    symbol TEXT NOT NULL,
    regime TEXT NOT NULL,
    side TEXT NOT NULL,
    role TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_bandit_pull_symbol ON bandit_pull(symbol, regime, side, ts DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uq_bandit_pull_decision ON bandit_pull(decision_id);
