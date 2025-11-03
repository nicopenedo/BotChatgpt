ALTER TABLE trades ADD COLUMN IF NOT EXISTS tenant_id UUID;
ALTER TABLE trade_fill ADD COLUMN IF NOT EXISTS tenant_id UUID;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS tenant_id UUID;

UPDATE trades SET tenant_id = COALESCE(tenant_id, '00000000-0000-0000-0000-000000000000'::uuid);
UPDATE trade_fill SET tenant_id = COALESCE(tenant_id, '00000000-0000-0000-0000-000000000000'::uuid);
UPDATE orders SET tenant_id = COALESCE(tenant_id, '00000000-0000-0000-0000-000000000000'::uuid);

ALTER TABLE trades ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE trade_fill ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE orders ALTER COLUMN tenant_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS ix_trades_tenant_time ON trades(tenant_id, executed_at);
CREATE INDEX IF NOT EXISTS ix_fill_tenant_time ON trade_fill(tenant_id, executed_at);
CREATE INDEX IF NOT EXISTS ix_orders_tenant_time ON orders(tenant_id, transact_time);
