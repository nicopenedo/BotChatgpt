DROP VIEW IF EXISTS vw_trades_enriched;
DROP VIEW IF EXISTS vw_trade_annotations;
DROP VIEW IF EXISTS vw_daily_pnl;
DROP VIEW IF EXISTS vw_weekly_pnl;
DROP VIEW IF EXISTS vw_monthly_pnl;
DROP VIEW IF EXISTS vw_range_pnl;

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
        d.preset_id,
        attr.pnl_gross AS attr_pnl_gross,
        attr.pnl_net AS attr_pnl_net,
        attr.signal_edge AS attr_signal_edge,
        attr.fees_cost AS attr_fees_cost,
        attr.fees_bps AS attr_fees_bps,
        attr.slippage_cost AS attr_slippage_cost,
        attr.slippage_bps AS attr_slippage_bps,
        attr.timing_cost AS attr_timing_cost,
        attr.timing_bps AS attr_timing_bps
    FROM trades t
    LEFT JOIN positions p ON t.position_id = p.id
    LEFT JOIN orders o ON t.order_id = o.order_id
    LEFT JOIN managed_orders mo
        ON mo.exchange_order_id = t.order_id
        OR (mo.client_order_id IS NOT NULL AND o.client_order_id = mo.client_order_id)
    LEFT JOIN decisions d ON d.order_id = t.order_id OR (p.correlation_id IS NOT NULL AND d.decision_key = p.correlation_id)
    LEFT JOIN pnl_attr attr ON attr.trade_id = t.id
)
SELECT
    trade_id,
    symbol,
    side,
    executed_at,
    price,
    quantity,
    COALESCE(attr_fees_cost, fee) AS fee,
    COALESCE(attr_fees_bps,
             CASE
                 WHEN price IS NOT NULL AND quantity IS NOT NULL AND quantity <> 0 AND fee IS NOT NULL THEN
                     fee / NULLIF(price * quantity, 0) * 10000
                 ELSE NULL
             END) AS fees_bps,
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
    COALESCE(
        attr_pnl_gross,
        CASE
            WHEN side = 'SELL' AND entry_price IS NOT NULL THEN (price - entry_price) * quantity
            WHEN side = 'BUY' AND entry_price IS NOT NULL THEN (entry_price - price) * quantity
            ELSE 0
        END
    ) AS pnl,
    COALESCE(attr_signal_edge,
        COALESCE(attr_pnl_gross,
            CASE
                WHEN side = 'SELL' AND entry_price IS NOT NULL THEN (price - entry_price) * quantity
                WHEN side = 'BUY' AND entry_price IS NOT NULL THEN (entry_price - price) * quantity
                ELSE 0
            END)) AS signal_edge,
    COALESCE(attr_pnl_net,
        COALESCE(attr_pnl_gross,
            CASE
                WHEN side = 'SELL' AND entry_price IS NOT NULL THEN (price - entry_price) * quantity
                WHEN side = 'BUY' AND entry_price IS NOT NULL THEN (entry_price - price) * quantity
                ELSE 0
            END)
        - COALESCE(attr_fees_cost, fee)
        - COALESCE(attr_slippage_cost, 0)
        - COALESCE(attr_timing_cost, 0)) AS pnl_net,
    CASE
        WHEN entry_price IS NOT NULL AND stop_loss IS NOT NULL AND stop_loss <> 0 THEN
            CASE
                WHEN side = 'SELL' THEN (price - entry_price) / NULLIF(entry_price - stop_loss, 0)
                WHEN side = 'BUY' THEN (entry_price - price) / NULLIF(stop_loss - entry_price, 0)
                ELSE NULL
            END
        ELSE NULL
    END AS pnl_r,
    COALESCE(
        attr_slippage_bps,
        CASE
            WHEN managed_order_price IS NOT NULL AND managed_order_price <> 0 THEN
                ((price - managed_order_price) / managed_order_price)
                * CASE WHEN side = 'SELL' THEN -10000 ELSE 10000 END
            ELSE NULL
        END) AS slippage_bps,
    attr_slippage_cost AS slippage_cost,
    attr_timing_bps AS timing_bps,
    attr_timing_cost AS timing_cost,
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
    SUM(COALESCE(pnl_net, pnl)) AS net_pnl,
    SUM(COALESCE(fee, 0)) AS fees
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
    SUM(COALESCE(pnl_net, pnl)) AS net_pnl,
    SUM(COALESCE(fee, 0)) AS fees
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
    SUM(COALESCE(pnl_net, pnl)) AS net_pnl,
    SUM(COALESCE(fee, 0)) AS fees
FROM vw_trades_enriched
GROUP BY symbol, date_trunc('month', executed_at)
ORDER BY period_start;

CREATE OR REPLACE VIEW vw_range_pnl AS
SELECT
    symbol,
    MIN(executed_at) AS period_start,
    MAX(executed_at) AS period_end,
    'Range' AS label,
    COUNT(*) AS trades,
    SUM(CASE WHEN pnl > 0 THEN 1 ELSE 0 END) AS wins,
    SUM(CASE WHEN pnl < 0 THEN 1 ELSE 0 END) AS losses,
    SUM(CASE WHEN pnl > 0 THEN pnl ELSE 0 END) AS gross_wins,
    SUM(CASE WHEN pnl < 0 THEN pnl ELSE 0 END) AS gross_losses,
    SUM(COALESCE(pnl_net, pnl)) AS net_pnl,
    SUM(COALESCE(fee, 0)) AS fees
FROM vw_trades_enriched
GROUP BY symbol;
