CREATE TABLE IF NOT EXISTS trade_fill (
    id BIGSERIAL PRIMARY KEY,
    order_id VARCHAR(120) NOT NULL,
    client_order_id VARCHAR(120),
    symbol VARCHAR(20) NOT NULL,
    order_type VARCHAR(20) NOT NULL,
    order_side VARCHAR(10) NOT NULL,
    ref_price NUMERIC(18,8),
    fill_price NUMERIC(18,8),
    slippage_bps DOUBLE PRECISION,
    queue_time_ms BIGINT,
    executed_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_trade_fill_symbol_time ON trade_fill(symbol, executed_at DESC);
CREATE INDEX IF NOT EXISTS idx_trade_fill_symbol_type ON trade_fill(symbol, order_type);
