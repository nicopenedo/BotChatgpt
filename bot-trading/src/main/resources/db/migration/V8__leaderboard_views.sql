CREATE OR REPLACE VIEW leaderboard_latest_shadow AS
SELECT DISTINCT ON (preset_id)
    preset_id,
    window,
    shadow_metrics_json,
    created_at
FROM evaluation_snapshots
WHERE window ILIKE 'SHADOW%'
ORDER BY preset_id, created_at DESC;

CREATE OR REPLACE VIEW leaderboard_latest_live AS
SELECT DISTINCT ON (preset_id)
    preset_id,
    window,
    live_metrics_json,
    created_at
FROM evaluation_snapshots
WHERE window ILIKE 'LIVE%'
ORDER BY preset_id, created_at DESC;
