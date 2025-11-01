ALTER TABLE decisions
    ADD COLUMN IF NOT EXISTS regime_trend VARCHAR(32),
    ADD COLUMN IF NOT EXISTS regime_volatility VARCHAR(32),
    ADD COLUMN IF NOT EXISTS preset_key VARCHAR(128),
    ADD COLUMN IF NOT EXISTS preset_id UUID;

ALTER TABLE positions
    ADD COLUMN IF NOT EXISTS regime_trend VARCHAR(32),
    ADD COLUMN IF NOT EXISTS regime_volatility VARCHAR(32),
    ADD COLUMN IF NOT EXISTS preset_key VARCHAR(128),
    ADD COLUMN IF NOT EXISTS preset_id UUID;

ALTER TABLE shadow_positions
    ADD COLUMN IF NOT EXISTS regime_trend VARCHAR(32),
    ADD COLUMN IF NOT EXISTS regime_volatility VARCHAR(32),
    ADD COLUMN IF NOT EXISTS preset_key VARCHAR(128),
    ADD COLUMN IF NOT EXISTS preset_id UUID;

CREATE TABLE IF NOT EXISTS risk_var_snapshot (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(32) NOT NULL,
    regime VARCHAR(64),
    regime_trend VARCHAR(32),
    regime_volatility VARCHAR(32),
    preset_id UUID,
    preset_key VARCHAR(128),
    position_id BIGINT,
    ts TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    var_q NUMERIC(18,8) NOT NULL,
    cvar_q NUMERIC(18,8) NOT NULL,
    qty_ratio NUMERIC(12,6),
    reasons_json JSONB,
    CONSTRAINT fk_risk_var_position FOREIGN KEY (position_id) REFERENCES positions(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_risk_var_snapshot_symbol_ts ON risk_var_snapshot(symbol, ts DESC);
CREATE INDEX IF NOT EXISTS idx_risk_var_snapshot_preset ON risk_var_snapshot(preset_id, ts DESC);

DROP VIEW IF EXISTS vw_trades_enriched;
DROP VIEW IF EXISTS vw_trade_annotations;
DROP VIEW IF EXISTS vw_daily_pnl;
DROP VIEW IF EXISTS vw_weekly_pnl;
DROP VIEW IF EXISTS vw_monthly_pnl;
DROP VIEW IF EXISTS vw_range_pnl;
DROP VIEW IF EXISTS vw_trade_heatmap;

CREATE OR REPLACE VIEW vw_trades_enriched AS
WITH enriched AS (
    SELECT
        t.id AS trade_id,
        COALESCE(t.symbol, p.symbol, o.symbol) AS symbol,
        COALESCE(t.side, o.side, p.side) AS side,
        t.executed_at,
        t.price,
        t.quantity,
        t.fee,
        t.order_id,
        COALESCE(p.id, 0) AS position_id,
        p.status AS position_status,
        p.entry_price,
        p.take_profit,
        p.stop_loss,
        p.regime_trend AS position_regime_trend,
        p.regime_volatility AS position_regime_volatility,
        p.preset_key AS position_preset_key,
        p.preset_id AS position_preset_id,
        mo.type AS managed_order_type,
        mo.side AS managed_order_side,
        mo.price AS managed_order_price,
        mo.stop_price AS managed_order_stop_price,
        mo.created_at AS managed_order_created_at,
        COALESCE(mo.client_order_id, o.client_order_id) AS client_order_id,
        d.decision_key,
        d.reason AS decision_note,
        d.regime_trend,
        d.regime_volatility,
        d.preset_key,
        d.preset_id
    FROM trades t
    LEFT JOIN positions p ON t.position_id = p.id
    LEFT JOIN orders o ON t.order_id = o.order_id
    LEFT JOIN managed_orders mo
        ON mo.exchange_order_id = t.order_id
        OR (mo.client_order_id IS NOT NULL AND o.client_order_id = mo.client_order_id)
    LEFT JOIN decisions d ON d.order_id = t.order_id OR (p.correlation_id IS NOT NULL AND d.decision_key = p.correlation_id)
)
SELECT
    trade_id,
    symbol,
    side,
    executed_at,
    price,
    quantity,
    fee,
    position_status,
    entry_price,
    take_profit,
    stop_loss,
    position_regime_trend,
    position_regime_volatility,
    position_preset_key,
    position_preset_id,
    regime_trend,
    regime_volatility,
    preset_key,
    preset_id,
    managed_order_type,
    managed_order_side,
    managed_order_price,
    managed_order_stop_price,
    managed_order_created_at,
    client_order_id,
    decision_key,
    decision_note,
    CASE
        WHEN side = 'SELL' AND entry_price IS NOT NULL THEN (price - entry_price) * quantity
        WHEN side = 'BUY' AND entry_price IS NOT NULL THEN (entry_price - price) * quantity
        ELSE 0
    END AS pnl,
    CASE
        WHEN entry_price IS NOT NULL AND stop_loss IS NOT NULL AND stop_loss <> 0 THEN
            CASE
                WHEN side = 'SELL' THEN (price - entry_price) / NULLIF(entry_price - stop_loss, 0)
                WHEN side = 'BUY' THEN (entry_price - price) / NULLIF(stop_loss - entry_price, 0)
                ELSE NULL
            END
        ELSE NULL
    END AS pnl_r,
    CASE
        WHEN managed_order_price IS NOT NULL AND managed_order_price <> 0 THEN
            ((price - managed_order_price) / managed_order_price)
            * CASE WHEN side = 'SELL' THEN -10000 ELSE 10000 END
        ELSE NULL
    END AS slippage_bps,
    CASE
        WHEN managed_order_type = 'STOP_LOSS' THEN 'SL'
        WHEN managed_order_type = 'TAKE_PROFIT' THEN 'TP'
        WHEN managed_order_type = 'TRAILING' THEN 'TRAIL'
        WHEN managed_order_type = 'BREAKEVEN' THEN 'BE'
        WHEN side = 'SELL' THEN 'SELL'
        WHEN side = 'BUY' THEN 'BUY'
        ELSE 'UNKNOWN'
    END AS annotation_type
FROM enriched;

CREATE OR REPLACE VIEW vw_trade_annotations AS
SELECT
    trade_id,
    symbol,
    annotation_type AS type,
    executed_at,
    price,
    quantity,
    pnl,
    pnl_r,
    fee,
    slippage_bps,
    COALESCE(decision_note, annotation_type) AS text
FROM vw_trades_enriched;

CREATE OR REPLACE VIEW vw_daily_pnl AS
SELECT
    symbol,
    date_trunc('day', executed_at) AS period_start,
    date_trunc('day', executed_at) + INTERVAL '1 day' AS period_end,
    to_char(date_trunc('day', executed_at), 'YYYY-MM-DD') AS label,
    COUNT(*) AS trades,
    SUM(CASE WHEN pnl > 0 THEN 1 ELSE 0 END) AS wins,
    SUM(CASE WHEN pnl < 0 THEN 1 ELSE 0 END) AS losses,
    SUM(CASE WHEN pnl > 0 THEN pnl ELSE 0 END) AS gross_wins,
    SUM(CASE WHEN pnl < 0 THEN pnl ELSE 0 END) AS gross_losses,
    SUM(pnl) AS net_pnl,
    SUM(fee) AS fees
FROM vw_trades_enriched
GROUP BY symbol, date_trunc('day', executed_at)
ORDER BY period_start;

CREATE OR REPLACE VIEW vw_weekly_pnl AS
SELECT
    symbol,
    date_trunc('week', executed_at) AS period_start,
    date_trunc('week', executed_at) + INTERVAL '1 week' AS period_end,
    to_char(date_trunc('week', executed_at), 'IYYY-"W"IW') AS label,
    COUNT(*) AS trades,
    SUM(CASE WHEN pnl > 0 THEN 1 ELSE 0 END) AS wins,
    SUM(CASE WHEN pnl < 0 THEN 1 ELSE 0 END) AS losses,
    SUM(CASE WHEN pnl > 0 THEN pnl ELSE 0 END) AS gross_wins,
    SUM(CASE WHEN pnl < 0 THEN pnl ELSE 0 END) AS gross_losses,
    SUM(pnl) AS net_pnl,
    SUM(fee) AS fees
FROM vw_trades_enriched
GROUP BY symbol, date_trunc('week', executed_at)
ORDER BY period_start;

CREATE OR REPLACE VIEW vw_monthly_pnl AS
SELECT
    symbol,
    date_trunc('month', executed_at) AS period_start,
    (date_trunc('month', executed_at) + INTERVAL '1 month') AS period_end,
    to_char(date_trunc('month', executed_at), 'YYYY-MM') AS label,
    COUNT(*) AS trades,
    SUM(CASE WHEN pnl > 0 THEN 1 ELSE 0 END) AS wins,
    SUM(CASE WHEN pnl < 0 THEN 1 ELSE 0 END) AS losses,
    SUM(CASE WHEN pnl > 0 THEN pnl ELSE 0 END) AS gross_wins,
    SUM(CASE WHEN pnl < 0 THEN pnl ELSE 0 END) AS gross_losses,
    SUM(pnl) AS net_pnl,
    SUM(fee) AS fees
FROM vw_trades_enriched
GROUP BY symbol, date_trunc('month', executed_at)
ORDER BY period_start;

CREATE OR REPLACE VIEW vw_range_pnl AS
SELECT
    symbol,
    MIN(executed_at) AS period_start,
    MAX(executed_at) AS period_end,
    CONCAT(to_char(MIN(executed_at), 'YYYY-MM-DD'), ' → ', to_char(MAX(executed_at), 'YYYY-MM-DD')) AS label,
    COUNT(*) AS trades,
    SUM(CASE WHEN pnl > 0 THEN 1 ELSE 0 END) AS wins,
    SUM(CASE WHEN pnl < 0 THEN 1 ELSE 0 END) AS losses,
    SUM(CASE WHEN pnl > 0 THEN pnl ELSE 0 END) AS gross_wins,
    SUM(CASE WHEN pnl < 0 THEN pnl ELSE 0 END) AS gross_losses,
    SUM(pnl) AS net_pnl,
    SUM(fee) AS fees
FROM vw_trades_enriched
GROUP BY symbol;

CREATE OR REPLACE VIEW vw_trade_heatmap AS
SELECT
    'HOUR_WEEKDAY' AS bucket,
    EXTRACT(HOUR FROM executed_at)::INT AS bucket_x,
    (EXTRACT(DOW FROM executed_at)::INT + 6) % 7 AS bucket_y,
    symbol,
    COUNT(*) AS trades,
    SUM(pnl) AS net_pnl,
    CASE WHEN COUNT(*) = 0 THEN 0 ELSE SUM(CASE WHEN pnl > 0 THEN 1 ELSE 0 END)::DECIMAL / COUNT(*) END AS win_rate
FROM vw_trades_enriched
GROUP BY symbol, EXTRACT(HOUR FROM executed_at)::INT, (EXTRACT(DOW FROM executed_at)::INT + 6) % 7
UNION ALL
SELECT
    'WEEKDAY' AS bucket,
    (EXTRACT(DOW FROM executed_at)::INT + 6) % 7 AS bucket_x,
    0 AS bucket_y,
    symbol,
    COUNT(*) AS trades,
    SUM(pnl) AS net_pnl,
    CASE WHEN COUNT(*) = 0 THEN 0 ELSE SUM(CASE WHEN pnl > 0 THEN 1 ELSE 0 END)::DECIMAL / COUNT(*) END AS win_rate
FROM vw_trades_enriched
GROUP BY symbol, (EXTRACT(DOW FROM executed_at)::INT + 6) % 7;
