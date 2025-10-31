CREATE TABLE IF NOT EXISTS orders (
    id SERIAL PRIMARY KEY,
    order_id VARCHAR(100),
    client_order_id VARCHAR(100),
    symbol VARCHAR(50),
    side VARCHAR(10),
    type VARCHAR(10),
    price NUMERIC(18,8),
    quantity NUMERIC(18,8),
    executed_qty NUMERIC(18,8),
    quote_qty NUMERIC(18,8),
    status VARCHAR(50),
    transact_time TIMESTAMP
);

CREATE TABLE IF NOT EXISTS trades (
    id SERIAL PRIMARY KEY,
    order_id VARCHAR(100),
    symbol VARCHAR(50),
    price NUMERIC(18,8),
    quantity NUMERIC(18,8),
    quote_qty NUMERIC(18,8),
    trade_time TIMESTAMP,
    fee NUMERIC(18,8)
);

CREATE TABLE IF NOT EXISTS positions (
    id SERIAL PRIMARY KEY,
    symbol VARCHAR(50),
    entry_price NUMERIC(18,8),
    quantity NUMERIC(18,8),
    stop_loss NUMERIC(18,8),
    take_profit NUMERIC(18,8),
    opened_at TIMESTAMP,
    closed_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS metrics_snapshots (
    id SERIAL PRIMARY KEY,
    captured_at TIMESTAMP,
    equity_value NUMERIC(18,8),
    drawdown_pct NUMERIC(18,8),
    daily_pnl_pct NUMERIC(18,8)
);
