package com.sevak.api.repository;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminRepository {

    private final JdbcTemplate jdbc;

    public AdminRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── Revenue by Category (Proportional Allocation via Window Function) ───
    public List<Map<String, Object>> getRevenueByCat() {
        return jdbc.queryForList("""
                WITH item_totals AS (
                    SELECT bi.booking_id,
                           s.category_id,
                           SUM(bi.quantity * bi.unit_price)                                    AS cat_amount,
                           SUM(SUM(bi.quantity * bi.unit_price)) OVER (PARTITION BY bi.booking_id) AS booking_items_total
                    FROM booking_items bi
                    JOIN services s ON s.service_id = bi.service_id
                    GROUP BY bi.booking_id, s.category_id
                )
                SELECT c.category_name,
                       COUNT(DISTINCT it.booking_id)                                              AS total_bookings,
                       ROUND(SUM(p.amount * it.cat_amount / NULLIF(it.booking_items_total, 0)), 2) AS total_revenue
                FROM item_totals it
                JOIN payments   p ON p.booking_id  = it.booking_id AND p.status = 'success'
                JOIN categories c ON c.category_id = it.category_id
                GROUP BY c.category_name
                ORDER BY total_revenue DESC
                """);
    }

    // ── Provider Leaderboard (RANK() Window Function!) ──────
    public List<Map<String, Object>> getLeaderboard() {
        return jdbc.queryForList("""
                SELECT
                    RANK() OVER (ORDER BY sp.avg_rating DESC NULLS LAST,
                                 COUNT(DISTINCT b.booking_id) DESC)  AS rank,
                    sp.provider_id,
                    u.email,
                    ci.city_name,
                    sp.avg_rating,
                    COUNT(DISTINCT b.booking_id)                     AS jobs_completed,
                    COALESCE(SUM(p.amount), 0)                       AS total_revenue
                FROM service_providers sp
                JOIN users u ON sp.user_id = u.user_id
                JOIN cities ci ON sp.city_id = ci.city_id
                LEFT JOIN bookings b ON sp.provider_id = b.provider_id
                                    AND b.status = 'completed'
                LEFT JOIN payments p ON b.booking_id = p.booking_id
                                    AND p.status = 'success'
                WHERE sp.is_active = TRUE
                GROUP BY sp.provider_id, u.email, ci.city_name, sp.avg_rating
                ORDER BY rank
                """);
    }

    // ── Complaint Dashboard (STRING_AGG, 5-table JOIN) ──────
    public List<Map<String, Object>> getComplaintDashboard() {
        return jdbc.queryForList("""
                SELECT cmp.status AS complaint_status,
                       COUNT(cmp.complaint_id) AS count,
                       STRING_AGG(DISTINCT ci.city_name, ', ') AS affected_cities
                FROM complaints cmp
                JOIN bookings b ON cmp.booking_id = b.booking_id
                JOIN locations loc ON b.location_id = loc.location_id
                JOIN areas ar ON loc.area_id = ar.area_id
                JOIN cities ci ON ar.city_id = ci.city_id
                GROUP BY cmp.status
                ORDER BY count DESC
                """);
    }

    // ── Leaderboard from Materialized View (Pre-computed read) ───
    public List<Map<String, Object>> getLeaderboardFast() {
        return jdbc.queryForList("SELECT * FROM mv_provider_leaderboard ORDER BY rank");
    }

    // ── City Revenue from Materialized View ───────────────
    public List<Map<String, Object>> getCityRevenue() {
        return jdbc.queryForList("SELECT * FROM mv_city_revenue_summary ORDER BY total_revenue DESC");
    }

    // ── Refresh Materialized Views ────────────────────────
    public Map<String, Object> refreshViews() {
        jdbc.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_provider_leaderboard");
        jdbc.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_city_revenue_summary");
        return Map.of(
                "success",
                true,
                "message",
                "Materialized views refreshed successfully",
                "timestamp",
                java.time.Instant.now().toString());
    }
}
