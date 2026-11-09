-- ============================================================
--  SEVAK: leaderboard view and materialized views (repeatable)
--  Runs before the seed file, which refreshes the materialized
--  views once the data is loaded.
-- ============================================================

DROP MATERIALIZED VIEW IF EXISTS mv_provider_leaderboard;
DROP MATERIALIZED VIEW IF EXISTS mv_city_revenue_summary;
DROP VIEW IF EXISTS v_provider_leaderboard;

-- One definition for the live leaderboard and its materialized copy.
-- weighted_rating is a Bayesian average: with few reviews a provider stays near the
-- platform average, so one 5-star review can't beat fifty excellent ones.
-- 5 is how many reviews it takes before a provider's own average starts to dominate.
CREATE VIEW v_provider_leaderboard AS
WITH platform AS (
    SELECT AVG(rating) AS avg_rating FROM provider_reviews
),
stats AS (
    SELECT sp.provider_id,
           u.email,
           ci.city_name,
           sp.avg_rating,
           sp.review_count,
           CASE WHEN sp.review_count > 0 THEN
               ROUND((5 * pl.avg_rating + sp.avg_rating * sp.review_count) / (5 + sp.review_count), 2)
           END                          AS weighted_rating,
           COUNT(DISTINCT b.booking_id) AS jobs_completed,
           COALESCE(SUM(p.amount), 0)   AS total_revenue
    FROM service_providers sp
    JOIN users u   ON sp.user_id = u.user_id
    JOIN cities ci ON sp.city_id = ci.city_id
    CROSS JOIN platform pl
    LEFT JOIN bookings b ON sp.provider_id = b.provider_id AND b.status = 'completed'
    LEFT JOIN payments p ON b.booking_id = p.booking_id AND p.status = 'success'
    WHERE sp.is_active = TRUE
    GROUP BY sp.provider_id, u.email, ci.city_name, sp.avg_rating, sp.review_count, pl.avg_rating
)
SELECT RANK() OVER (ORDER BY weighted_rating DESC NULLS LAST, jobs_completed DESC) AS rank,
       provider_id, email, city_name, avg_rating, review_count, weighted_rating, jobs_completed, total_revenue
FROM stats;

CREATE MATERIALIZED VIEW mv_provider_leaderboard AS
SELECT * FROM v_provider_leaderboard ORDER BY rank, provider_id;

CREATE UNIQUE INDEX idx_mv_leaderboard_provider ON mv_provider_leaderboard (provider_id);

CREATE MATERIALIZED VIEW mv_city_revenue_summary AS
SELECT
    ci.city_name,
    COUNT(DISTINCT b.booking_id)         AS total_bookings,
    COUNT(DISTINCT b.customer_id)        AS unique_customers,
    COALESCE(SUM(p.amount), 0)           AS total_revenue,
    ROUND(COALESCE(AVG(p.amount), 0), 2) AS avg_booking_value
FROM cities ci
JOIN areas ar       ON ci.city_id      = ar.city_id
JOIN locations loc  ON ar.area_id      = loc.area_id
JOIN bookings b     ON loc.location_id = b.location_id
LEFT JOIN payments p ON b.booking_id   = p.booking_id AND p.status = 'success'
GROUP BY ci.city_name
ORDER BY total_revenue DESC;

CREATE UNIQUE INDEX idx_mv_city_revenue ON mv_city_revenue_summary (city_name);
