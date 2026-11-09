-- ============================================================
--  SEVAK: materialized views (repeatable)
--  Runs before the seed file, which refreshes these views once
--  the data is loaded.
-- ============================================================

DROP MATERIALIZED VIEW IF EXISTS mv_provider_leaderboard;
DROP MATERIALIZED VIEW IF EXISTS mv_city_revenue_summary;

-- ============================================================
--  MATERIALIZED VIEWS
-- ============================================================

-- 1. Provider Leaderboard — pre-computes RANK() window function
CREATE MATERIALIZED VIEW mv_provider_leaderboard AS
SELECT
    RANK() OVER (ORDER BY sp.avg_rating DESC NULLS LAST,
                 COUNT(DISTINCT b.booking_id) DESC)       AS rank,
    sp.provider_id,
    u.email,
    ci.city_name,
    sp.avg_rating,
    COUNT(DISTINCT b.booking_id)                          AS jobs_completed,
    COALESCE(SUM(p.amount), 0)                            AS total_revenue
FROM service_providers sp
JOIN users u    ON sp.user_id     = u.user_id
JOIN cities ci  ON sp.city_id     = ci.city_id
LEFT JOIN bookings b ON sp.provider_id = b.provider_id
                    AND b.status = 'completed'
LEFT JOIN payments p ON b.booking_id = p.booking_id
                    AND p.status = 'success'
WHERE sp.is_active = TRUE
GROUP BY sp.provider_id, u.email, ci.city_name, sp.avg_rating
ORDER BY rank;

CREATE UNIQUE INDEX idx_mv_leaderboard_provider
    ON mv_provider_leaderboard(provider_id);

-- 2. City Revenue Summary — pre-computes 5-table revenue analytics
CREATE MATERIALIZED VIEW mv_city_revenue_summary AS
SELECT
    ci.city_name,
    COUNT(DISTINCT b.booking_id)                 AS total_bookings,
    COUNT(DISTINCT b.customer_id)                AS unique_customers,
    COALESCE(SUM(p.amount), 0)                   AS total_revenue,
    ROUND(COALESCE(AVG(p.amount), 0), 2)         AS avg_booking_value
FROM cities ci
JOIN areas ar       ON ci.city_id       = ar.city_id
JOIN locations loc  ON ar.area_id       = loc.area_id
JOIN bookings b     ON loc.location_id  = b.location_id
LEFT JOIN payments p ON b.booking_id    = p.booking_id
                    AND p.status = 'success'
GROUP BY ci.city_name
ORDER BY total_revenue DESC;

CREATE UNIQUE INDEX idx_mv_city_revenue
    ON mv_city_revenue_summary(city_name);

