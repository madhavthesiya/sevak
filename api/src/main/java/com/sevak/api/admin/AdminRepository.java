package com.sevak.api.admin;

import com.sevak.api.admin.dto.AdminBooking;
import com.sevak.api.admin.dto.CategoryRevenue;
import com.sevak.api.admin.dto.CityRevenue;
import com.sevak.api.admin.dto.ComplaintSummary;
import com.sevak.api.admin.dto.LeaderboardEntry;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AdminRepository {

    /** Any fixed number; every app instance must use the same one to share the lock. */
    private static final long VIEW_REFRESH_LOCK = 734_001L;

    private static final String BOOKINGS_SELECT = """
            SELECT b.booking_id, cu_u.full_name AS customer_name, pr_u.full_name AS provider_name,
                   b.scheduled_start, b.status, b.total_amount
            FROM bookings b
            JOIN customers cu ON cu.customer_id = b.customer_id
            JOIN users cu_u ON cu_u.user_id = cu.user_id
            JOIN service_providers sp ON sp.provider_id = b.provider_id
            JOIN users pr_u ON pr_u.user_id = sp.user_id
            """;

    private final JdbcClient jdbc;
    private final JdbcTemplate template;

    public AdminRepository(JdbcClient jdbc, JdbcTemplate template) {
        this.jdbc = jdbc;
        this.template = template;
    }

    /** Each payment is split across categories in proportion to item value, so nothing is counted twice. */
    public List<CategoryRevenue> findRevenueByCategory() {
        return jdbc.sql("""
                        WITH item_totals AS (
                            SELECT bi.booking_id,
                                   s.category_id,
                                   SUM(bi.quantity * bi.unit_price) AS cat_amount,
                                   SUM(SUM(bi.quantity * bi.unit_price)) OVER (PARTITION BY bi.booking_id) AS booking_items_total
                            FROM booking_items bi
                            JOIN services s ON s.service_id = bi.service_id
                            GROUP BY bi.booking_id, s.category_id
                        )
                        SELECT c.category_name,
                               COUNT(DISTINCT it.booking_id) AS total_bookings,
                               ROUND(SUM(p.amount * it.cat_amount / NULLIF(it.booking_items_total, 0)), 2) AS total_revenue
                        FROM item_totals it
                        JOIN payments   p ON p.booking_id  = it.booking_id AND p.status = 'success'
                        JOIN categories c ON c.category_id = it.category_id
                        GROUP BY c.category_name
                        ORDER BY total_revenue DESC, c.category_name
                        """).query(CategoryRevenue.class).list();
    }

    public List<LeaderboardEntry> findLeaderboard() {
        return jdbc.sql("SELECT * FROM v_provider_leaderboard ORDER BY rank, provider_id")
                .query(LeaderboardEntry.class)
                .list();
    }

    public List<LeaderboardEntry> findLeaderboardFromView() {
        return jdbc.sql("SELECT * FROM mv_provider_leaderboard ORDER BY rank, provider_id")
                .query(LeaderboardEntry.class)
                .list();
    }

    public List<ComplaintSummary> findComplaintSummary() {
        return jdbc.sql("""
                        SELECT cmp.status AS complaint_status,
                               COUNT(cmp.complaint_id) AS count,
                               STRING_AGG(DISTINCT ci.city_name, ', ') AS affected_cities
                        FROM complaints cmp
                        JOIN bookings b ON cmp.booking_id = b.booking_id
                        JOIN locations loc ON b.location_id = loc.location_id
                        JOIN areas ar ON loc.area_id = ar.area_id
                        JOIN cities ci ON ar.city_id = ci.city_id
                        GROUP BY cmp.status
                        ORDER BY count DESC, cmp.status
                        """).query(ComplaintSummary.class).list();
    }

    public List<CityRevenue> findCityRevenue() {
        return jdbc.sql("SELECT * FROM mv_city_revenue_summary ORDER BY total_revenue DESC, city_name")
                .query(CityRevenue.class)
                .list();
    }

    /** The first page of all bookings, newest first. */
    public List<AdminBooking> findBookingsFirstPage(int limit) {
        return jdbc.sql(BOOKINGS_SELECT + "ORDER BY b.scheduled_start DESC, b.booking_id DESC LIMIT :limit")
                .param("limit", limit)
                .query(AdminBooking.class)
                .list();
    }

    /** Keyset pagination: continues directly after the last row of the previous page. */
    public List<AdminBooking> findBookingsBefore(OffsetDateTime before, int beforeId, int limit) {
        return jdbc.sql(BOOKINGS_SELECT + """
                        WHERE (b.scheduled_start, b.booking_id) < (:before, :beforeId)
                        ORDER BY b.scheduled_start DESC, b.booking_id DESC
                        LIMIT :limit
                        """)
                .param("before", before)
                .param("beforeId", beforeId)
                .param("limit", limit)
                .query(AdminBooking.class)
                .list();
    }

    /**
     * Refreshes the materialized views unless another instance is already doing it.
     *
     * <p>Uses one connection throughout, because a session-level advisory lock belongs to the
     * connection that took it.
     *
     * @return false if another connection held the lock and nothing was refreshed
     */
    public boolean refreshViewsIfNotRunning() {
        return Boolean.TRUE.equals(template.execute((ConnectionCallback<Boolean>) connection -> {
            try (PreparedStatement lock = connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
                lock.setLong(1, VIEW_REFRESH_LOCK);
                try (ResultSet result = lock.executeQuery()) {
                    result.next();
                    if (!result.getBoolean(1)) {
                        return false;
                    }
                }
            }
            try (var statement = connection.createStatement()) {
                statement.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_provider_leaderboard");
                statement.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_city_revenue_summary");
                return true;
            } finally {
                try (PreparedStatement unlock = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
                    unlock.setLong(1, VIEW_REFRESH_LOCK);
                    unlock.execute();
                }
            }
        }));
    }

    public int setProviderVerification(int providerId, String status) {
        return jdbc.sql("UPDATE service_providers SET verification_status = :status WHERE provider_id = :id")
                .param("status", status)
                .param("id", providerId)
                .update();
    }

    public void reviewProviderDocuments(int providerId, String status, int adminId) {
        jdbc.sql("""
                        UPDATE provider_documents
                        SET status = :status, verified_by = :admin, verified_at = now()
                        WHERE provider_id = :id AND status = 'pending'
                        """)
                .param("status", status)
                .param("admin", adminId)
                .param("id", providerId)
                .update();
    }

    public int updateComplaint(int complaintId, String status, Integer assignedAdminId, String notes) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", complaintId);
        params.put("status", status);
        params.put("admin", assignedAdminId);
        params.put("notes", notes);
        return jdbc.sql("""
                        UPDATE complaints
                        SET status = :status,
                            assigned_admin_id = COALESCE(CAST(:admin AS int), assigned_admin_id),
                            resolution_notes = COALESCE(CAST(:notes AS text), resolution_notes),
                            resolved_at = CASE WHEN CAST(:status AS text) IN ('resolved', 'closed')
                                               THEN COALESCE(resolved_at, now()) END
                        WHERE complaint_id = :id
                        """).params(params).update();
    }

    public int processRefund(int refundId) {
        return jdbc.sql("""
                        UPDATE refunds SET status = 'processed', processed_at = now()
                        WHERE refund_id = :id AND status = 'pending'
                        """).param("id", refundId).update();
    }

    public boolean refundExists(int refundId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM refunds WHERE refund_id = :id)")
                .param("id", refundId)
                .query(Boolean.class)
                .single();
    }
}
