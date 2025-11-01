CREATE TABLE IF NOT EXISTS pnl_attr (
    trade_id BIGINT PRIMARY KEY,
    symbol VARCHAR(32) NOT NULL,
    signal VARCHAR(128),
    regime VARCHAR(64),
    preset VARCHAR(128),
    pnl_gross NUMERIC(18,8),
    signal_edge NUMERIC(18,8),
    fees_cost NUMERIC(18,8),
    fees_bps NUMERIC(12,4),
    slippage_cost NUMERIC(18,8),
    slippage_bps NUMERIC(12,4),
    timing_cost NUMERIC(18,8),
    timing_bps NUMERIC(12,4),
    pnl_net NUMERIC(18,8),
    notional NUMERIC(18,8),
    ts TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_attr_trade FOREIGN KEY (trade_id) REFERENCES trades(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_pnl_attr_symbol_ts ON pnl_attr(symbol, ts DESC);
CREATE INDEX IF NOT EXISTS idx_pnl_attr_preset ON pnl_attr(preset);
