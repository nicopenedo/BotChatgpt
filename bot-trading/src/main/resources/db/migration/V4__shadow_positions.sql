CREATE TABLE IF NOT EXISTS shadow_positions (
    id SERIAL PRIMARY KEY,
    symbol VARCHAR(50) NOT NULL,
    side VARCHAR(10),
    entry_price NUMERIC(18,8),
    exit_price NUMERIC(18,8),
    stop_loss NUMERIC(18,8),
    take_profit NUMERIC(18,8),
    status VARCHAR(20) DEFAULT 'OPEN',
    quantity NUMERIC(18,8),
    opened_at TIMESTAMP,
    closed_at TIMESTAMP,
    realized_pnl NUMERIC(18,8),
    trades INTEGER DEFAULT 0
);
