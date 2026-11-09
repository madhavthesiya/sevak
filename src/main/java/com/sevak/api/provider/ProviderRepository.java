package com.sevak.api.provider;

import com.sevak.api.provider.dto.AvailabilitySlot;
import com.sevak.api.provider.dto.EarningsByMethod;
import com.sevak.api.provider.dto.JobSummary;
import com.sevak.api.provider.dto.OfferedService;
import com.sevak.api.provider.dto.ProviderBasics;
import com.sevak.api.provider.dto.ProviderOffering;
import com.sevak.api.provider.dto.ProviderReview;
import com.sevak.api.provider.dto.TopProvider;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ProviderRepository {

    private static final String DAY_NAME =
            "(ARRAY['Monday','Tuesday','Wednesday','Thursday','Friday','Saturday','Sunday'])[pa.day_of_week]";

    private static final String FROM_OFFERINGS = """
            FROM provider_services ps
            JOIN services s           ON s.service_id = ps.service_id
            JOIN service_providers sp ON sp.provider_id = ps.provider_id
            JOIN users u              ON u.user_id = sp.user_id
            JOIN cities ci            ON ci.city_id = sp.city_id
            WHERE ps.is_active AND sp.is_active AND sp.verification_status = 'verified'
              AND (CAST(:service AS text) IS NULL OR s.service_name ILIKE :service ESCAPE '\\')
              AND (CAST(:city AS text) IS NULL OR LOWER(ci.city_name) = LOWER(:city))
              AND (CAST(:day AS int) IS NULL OR EXISTS (
                    SELECT 1 FROM provider_availability pa
                    WHERE pa.provider_id = sp.provider_id AND pa.day_of_week = CAST(:day AS int)))
            """;

    private final JdbcClient jdbc;

    public ProviderRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static Map<String, Object> offeringFilters(String servicePattern, String city, Integer day) {
        Map<String, Object> params = new HashMap<>();
        params.put("service", servicePattern);
        params.put("city", city);
        params.put("day", day);
        return params;
    }

    public List<ProviderOffering> findOfferings(
            String servicePattern, String city, Integer day, int limit, long offset) {
        Map<String, Object> params = offeringFilters(servicePattern, city, day);
        params.put("limit", limit);
        params.put("offset", offset);
        return jdbc.sql("""
                        WITH platform AS (SELECT AVG(rating) AS avg_rating FROM provider_reviews)
                        SELECT sp.provider_id, u.full_name AS provider_name, ci.city_name, s.service_id,
                               s.service_name, s.base_price, ps.price, sp.avg_rating, sp.review_count,
                               sp.experience_years
                        """ + FROM_OFFERINGS.replaceFirst("WHERE", "CROSS JOIN platform pl\nWHERE") + """
                        ORDER BY sp.review_count = 0,
                                 (5 * pl.avg_rating + COALESCE(sp.avg_rating, 0) * sp.review_count)
                                     / (5 + sp.review_count) DESC,
                                 sp.provider_id, s.service_id
                        LIMIT :limit OFFSET :offset
                        """)
                .params(params)
                .query(ProviderOffering.class)
                .list();
    }

    public long countOfferings(String servicePattern, String city, Integer day) {
        return jdbc.sql("SELECT COUNT(*) " + FROM_OFFERINGS)
                .params(offeringFilters(servicePattern, city, day))
                .query(Long.class)
                .single();
    }

    public List<TopProvider> findTop(int limit) {
        return jdbc.sql("""
                        SELECT lb.provider_id, u.full_name AS provider_name, lb.city_name, lb.avg_rating,
                               lb.review_count, lb.weighted_rating, sp.experience_years
                        FROM v_provider_leaderboard lb
                        JOIN service_providers sp ON sp.provider_id = lb.provider_id
                        JOIN users u ON u.user_id = sp.user_id
                        WHERE sp.verification_status = 'verified'
                        ORDER BY lb.weighted_rating DESC NULLS LAST, lb.provider_id
                        LIMIT :limit
                        """).param("limit", limit).query(TopProvider.class).list();
    }

    public boolean exists(int providerId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM service_providers WHERE provider_id = :id)")
                .param("id", providerId)
                .query(Boolean.class)
                .single();
    }

    /** The derived table compares this provider's rating with their city's average. */
    public Optional<ProviderBasics> findBasics(int providerId) {
        return jdbc.sql("""
                        SELECT sp.provider_id, u.full_name AS provider_name, ci.city_name, sp.bio,
                               sp.experience_years, sp.verification_status, sp.avg_rating, sp.review_count,
                               city_avg.avg_city_rating AS city_average_rating,
                               CASE
                                   WHEN sp.avg_rating IS NULL THEN 'No reviews yet'
                                   WHEN sp.avg_rating > city_avg.avg_city_rating THEN 'Above Average'
                                   WHEN sp.avg_rating = city_avg.avg_city_rating THEN 'Average'
                                   ELSE 'Below Average'
                               END AS compared_to_city
                        FROM service_providers sp
                        JOIN users u   ON u.user_id = sp.user_id
                        JOIN cities ci ON ci.city_id = sp.city_id
                        LEFT JOIN (
                            SELECT city_id, ROUND(AVG(avg_rating), 2) AS avg_city_rating
                            FROM service_providers
                            WHERE avg_rating IS NOT NULL
                            GROUP BY city_id
                        ) city_avg ON city_avg.city_id = sp.city_id
                        WHERE sp.provider_id = :id
                        """).param("id", providerId).query(ProviderBasics.class).optional();
    }

    public List<OfferedService> findServices(int providerId) {
        return jdbc.sql("""
                        SELECT s.service_id, s.service_name, s.base_price, ps.price
                        FROM provider_services ps
                        JOIN services s ON s.service_id = ps.service_id
                        WHERE ps.provider_id = :id AND ps.is_active
                        ORDER BY s.service_name, s.service_id
                        """).param("id", providerId).query(OfferedService.class).list();
    }

    public List<AvailabilitySlot> findAvailability(int providerId) {
        return jdbc.sql("SELECT " + DAY_NAME + """
                         AS day_of_week, pa.start_time, pa.end_time
                        FROM provider_availability pa
                        WHERE pa.provider_id = :id
                        ORDER BY pa.day_of_week, pa.start_time
                        """)
                .param("id", providerId)
                .query(AvailabilitySlot.class)
                .list();
    }

    public List<ProviderReview> findReviews(int providerId, int limit, long offset) {
        return jdbc.sql("""
                        SELECT pr.rating, pr.comment, cu_u.full_name AS customer_name, pr.created_at AS reviewed_at
                        FROM provider_reviews pr
                        JOIN bookings b    ON b.booking_id = pr.booking_id
                        JOIN customers cu  ON cu.customer_id = b.customer_id
                        JOIN users cu_u    ON cu_u.user_id = cu.user_id
                        WHERE b.provider_id = :id
                        ORDER BY pr.created_at DESC, pr.review_id DESC
                        LIMIT :limit OFFSET :offset
                        """)
                .param("id", providerId)
                .param("limit", limit)
                .param("offset", offset)
                .query(ProviderReview.class)
                .list();
    }

    public long countReviews(int providerId) {
        return jdbc.sql("""
                        SELECT COUNT(*) FROM provider_reviews pr
                        JOIN bookings b ON b.booking_id = pr.booking_id
                        WHERE b.provider_id = :id
                        """).param("id", providerId).query(Long.class).single();
    }

    /** Upcoming work is listed soonest first; finished work newest first. */
    public List<JobSummary> findJobs(int providerId, String status, int limit, long offset) {
        String order = status.equals("completed") || status.equals("cancelled") ? "DESC" : "ASC";
        return jdbc.sql("""
                        SELECT b.booking_id, cu_u.full_name AS customer_name, b.scheduled_start, b.scheduled_end,
                               b.status, b.total_amount,
                               CASE WHEN b.status IN ('confirmed', 'in_progress', 'completed') THEN loc.street END AS street,
                               ar.area_name, p.status AS payment_status
                        FROM bookings b
                        JOIN customers cu ON cu.customer_id = b.customer_id
                        JOIN users cu_u   ON cu_u.user_id = cu.user_id
                        JOIN locations loc ON loc.location_id = b.location_id
                        JOIN areas ar     ON ar.area_id = loc.area_id
                        LEFT JOIN LATERAL (
                            SELECT status FROM payments
                            WHERE booking_id = b.booking_id
                            ORDER BY created_at DESC, payment_id DESC
                            LIMIT 1
                        ) p ON TRUE
                        WHERE b.provider_id = :id AND b.status = :status
                        ORDER BY b.scheduled_start %1$s, b.booking_id %1$s
                        LIMIT :limit OFFSET :offset
                        """.formatted(order))
                .param("id", providerId)
                .param("status", status)
                .param("limit", limit)
                .param("offset", offset)
                .query(JobSummary.class)
                .list();
    }

    public long countJobs(int providerId, String status) {
        return jdbc.sql("SELECT COUNT(*) FROM bookings WHERE provider_id = :id AND status = :status")
                .param("id", providerId)
                .param("status", status)
                .query(Long.class)
                .single();
    }

    public List<EarningsByMethod> findEarnings(int providerId, LocalDate from, LocalDate to) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", providerId);
        params.put("from", from);
        params.put("to", to);
        return jdbc.sql("""
                        SELECT p.payment_method, COUNT(*) AS total_payments, SUM(p.amount) AS total_earned
                        FROM payments p
                        JOIN bookings b ON b.booking_id = p.booking_id
                        WHERE b.provider_id = :id AND p.status = 'success'
                          AND (CAST(:from AS date) IS NULL
                               OR CAST(p.paid_at AT TIME ZONE 'Asia/Kolkata' AS date) >= CAST(:from AS date))
                          AND (CAST(:to AS date) IS NULL
                               OR CAST(p.paid_at AT TIME ZONE 'Asia/Kolkata' AS date) <= CAST(:to AS date))
                        GROUP BY p.payment_method
                        ORDER BY total_earned DESC, p.payment_method
                        """).params(params).query(EarningsByMethod.class).list();
    }
}
