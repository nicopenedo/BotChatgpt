CREATE TABLE IF NOT EXISTS managed_orders (
    id SERIAL PRIMARY KEY,
    position_id BIGINT NOT NULL REFERENCES positions(id) ON DELETE CASCADE,
    client_order_id VARCHAR(100),
    type VARCHAR(20) NOT NULL,
    side VARCHAR(10) NOT NULL,
    price NUMERIC(18,8),
    quantity NUMERIC(18,8),
    filled_qty NUMERIC(18,8),
    status VARCHAR(50),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
