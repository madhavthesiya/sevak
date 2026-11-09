package com.sevak.api.booking;

import com.sevak.api.booking.dto.BookingCreated;
import com.sevak.api.booking.dto.BookingParties;
import com.sevak.api.booking.dto.IdempotencyRecord;
import com.sevak.api.booking.dto.PricedItem;
import com.sevak.api.booking.dto.RedeemedCoupon;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class BookingRepository {

    private final JdbcClient jdbc;

    public BookingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Lets database triggers record who made a change. Lasts until the transaction ends. */
    public void setActor(int userId) {
        jdbc.sql("SELECT set_config('sevak.user_id', :id, true)")
                .param("id", String.valueOf(userId))
                .query(String.class)
                .single();
    }

    // ── Creating a booking ──────────────────────────────────

    /**
     * Makes bookings for one provider happen one at a time until this transaction ends. Without it,
     * two overlapping inserts wait on each other and PostgreSQL has to abort one as a deadlock.
     */
    public void lockProviderBookings(int providerId) {
        jdbc.sql("SELECT pg_advisory_xact_lock(7340, :id)")
                .param("id", providerId)
                .query((rs, rowNum) -> rowNum)
                .single();
    }

    /** Returns true if this key is new. A concurrent request with the same key waits here. */
    public boolean claimIdempotencyKey(int userId, String key, String requestHash) {
        return jdbc.sql("""
                        INSERT INTO idempotency_keys (user_id, idempotency_key, request_hash)
                        VALUES (:user, :key, :hash)
                        ON CONFLICT (user_id, idempotency_key) DO NOTHING
                        """)
                        .param("user", userId)
                        .param("key", key)
                        .param("hash", requestHash)
                        .update()
                == 1;
    }

    public IdempotencyRecord findIdempotencyKey(int userId, String key) {
        return jdbc.sql("""
                        SELECT request_hash, booking_id FROM idempotency_keys
                        WHERE user_id = :user AND idempotency_key = :key
                        """)
                .param("user", userId)
                .param("key", key)
                .query(IdempotencyRecord.class)
                .single();
    }

    public void attachBookingToKey(int userId, String key, int bookingId) {
        jdbc.sql("""
                        UPDATE idempotency_keys SET booking_id = :booking
                        WHERE user_id = :user AND idempotency_key = :key
                        """)
                .param("booking", bookingId)
                .param("user", userId)
                .param("key", key)
                .update();
    }

    /** The area of one of this customer's active addresses. */
    public Optional<Integer> findActiveAddressArea(int customerId, int locationId) {
        return jdbc.sql("""
                        SELECT loc.area_id
                        FROM customer_addresses ca
                        JOIN locations loc ON loc.location_id = ca.location_id
                        WHERE ca.customer_id = :customer AND ca.location_id = :location AND ca.is_active
                        """)
                .param("customer", customerId)
                .param("location", locationId)
                .query(Integer.class)
                .optional();
    }

    public boolean providerServesArea(int providerId, int areaId) {
        return jdbc.sql("""
                        SELECT EXISTS (
                            SELECT 1 FROM service_providers sp
                            JOIN provider_service_areas psa ON psa.provider_id = sp.provider_id
                            WHERE sp.provider_id = :provider AND psa.area_id = :area
                              AND sp.is_active AND sp.verification_status = 'verified')
                        """)
                .param("provider", providerId)
                .param("area", areaId)
                .query(Boolean.class)
                .single();
    }

    /** Today's price and duration. Empty if the provider doesn't offer it or the variant doesn't match. */
    public Optional<PricedItem> priceItem(int providerId, int serviceId, Integer variantId) {
        Map<String, Object> params = new HashMap<>();
        params.put("provider", providerId);
        params.put("service", serviceId);
        params.put("variant", variantId);
        return jdbc.sql("""
                        SELECT COALESCE(sv.price, ps.price) AS unit_price, COALESCE(sv.duration, 60) AS duration
                        FROM provider_services ps
                        LEFT JOIN service_variants sv
                               ON sv.service_id = ps.service_id AND sv.variant_id = CAST(:variant AS int)
                        WHERE ps.provider_id = :provider AND ps.service_id = :service AND ps.is_active
                          AND (CAST(:variant AS int) IS NULL OR sv.variant_id IS NOT NULL)
                        """).params(params).query(PricedItem.class).optional();
    }

    public boolean providerAvailable(int providerId, OffsetDateTime start, OffsetDateTime end) {
        return jdbc.sql("""
                        SELECT fn_provider_available(:provider,
                                                     CAST(:start AS timestamptz) AT TIME ZONE 'Asia/Kolkata',
                                                     CAST(:end AS timestamptz) AT TIME ZONE 'Asia/Kolkata')
                        """)
                .param("provider", providerId)
                .param("start", start)
                .param("end", end)
                .query(Boolean.class)
                .single();
    }

    /** Atomic: the row is only updated if a use is left, so two requests can't take the last one. */
    public Optional<RedeemedCoupon> redeemCoupon(String code, BigDecimal subtotal) {
        return jdbc.sql("""
                        UPDATE coupons
                        SET used_count = used_count + 1
                        WHERE UPPER(code) = UPPER(:code)
                          AND (usage_limit IS NULL OR used_count < usage_limit)
                          AND CURRENT_DATE BETWEEN valid_from AND valid_to
                          AND COALESCE(min_order, 0) <= :subtotal
                        RETURNING coupon_id, discount_type, discount_value, max_discount
                        """)
                .param("code", code)
                .param("subtotal", subtotal)
                .query(RedeemedCoupon.class)
                .optional();
    }

    public boolean recordRedemption(int couponId, int customerId, int bookingId) {
        return jdbc.sql("""
                        INSERT INTO coupon_redemptions (coupon_id, customer_id, booking_id)
                        VALUES (:coupon, :customer, :booking)
                        ON CONFLICT (coupon_id, customer_id) DO NOTHING
                        """)
                        .param("coupon", couponId)
                        .param("customer", customerId)
                        .param("booking", bookingId)
                        .update()
                == 1;
    }

    public int insertBooking(
            int customerId,
            int providerId,
            int locationId,
            Integer couponId,
            OffsetDateTime start,
            OffsetDateTime end,
            BigDecimal subtotal,
            BigDecimal discount,
            String instructions) {
        Map<String, Object> params = new HashMap<>();
        params.put("customer", customerId);
        params.put("provider", providerId);
        params.put("location", locationId);
        params.put("coupon", couponId);
        params.put("start", start);
        params.put("end", end);
        params.put("subtotal", subtotal);
        params.put("discount", discount);
        params.put("total", subtotal.subtract(discount));
        params.put("instructions", instructions);
        return jdbc.sql("""
                        INSERT INTO bookings (customer_id, provider_id, location_id, coupon_id, scheduled_start,
                                              scheduled_end, status, subtotal, discount, total_amount,
                                              special_instructions)
                        VALUES (:customer, :provider, :location, :coupon, :start, :end, 'pending', :subtotal,
                                :discount, :total, :instructions)
                        RETURNING booking_id
                        """).params(params).query(Integer.class).single();
    }

    public void insertItem(
            int bookingId, int itemNo, int serviceId, Integer variantId, int quantity, BigDecimal unitPrice) {
        Map<String, Object> params = new HashMap<>();
        params.put("booking", bookingId);
        params.put("item", itemNo);
        params.put("service", serviceId);
        params.put("variant", variantId);
        params.put("quantity", quantity);
        params.put("price", unitPrice);
        jdbc.sql("""
                        INSERT INTO booking_items (booking_id, item_no, service_id, variant_id, quantity, unit_price)
                        VALUES (:booking, :item, :service, :variant, :quantity, :price)
                        """).params(params).update();
    }

    public BookingCreated findCreated(int bookingId) {
        return jdbc.sql("""
                        SELECT booking_id, status, scheduled_start, scheduled_end, subtotal, discount, total_amount
                        FROM bookings WHERE booking_id = :id
                        """).param("id", bookingId).query(BookingCreated.class).single();
    }

    // ── After a booking exists ──────────────────────────────

    public Optional<BookingParties> findParties(int bookingId) {
        return jdbc.sql("""
                        SELECT customer_id, provider_id, status, scheduled_start, total_amount
                        FROM bookings WHERE booking_id = :id
                        """).param("id", bookingId).query(BookingParties.class).optional();
    }

    public void cancel(int bookingId, String reason, int cancelledBy) {
        jdbc.sql("CALL sp_cancel_booking(:id, :reason, :by)")
                .param("id", bookingId)
                .param("reason", reason)
                .param("by", cancelledBy)
                .update();
    }

    public void confirm(int bookingId) {
        jdbc.sql("CALL sp_confirm_booking(:id)").param("id", bookingId).update();
    }

    /** The status-rules trigger rejects any change that isn't allowed. */
    public void setStatus(int bookingId, String status) {
        jdbc.sql("UPDATE bookings SET status = :status WHERE booking_id = :id")
                .param("status", status)
                .param("id", bookingId)
                .update();
    }

    public int insertPayment(int bookingId, String method, String status, BigDecimal amount, String failureReason) {
        Map<String, Object> params = new HashMap<>();
        params.put("booking", bookingId);
        params.put("method", method);
        params.put("status", status);
        params.put("amount", amount);
        params.put("ref", "success".equals(status) ? "MOCK_" + UUID.randomUUID() : null);
        params.put("failure", failureReason);
        return jdbc.sql("""
                        INSERT INTO payments (booking_id, payment_method, status, amount, gateway_ref, paid_at,
                                              failure_reason)
                        VALUES (:booking, :method, :status, :amount, :ref,
                                CASE WHEN CAST(:status AS text) = 'success' THEN now() END, :failure)
                        RETURNING payment_id
                        """).params(params).query(Integer.class).single();
    }

    public String findGatewayRef(int paymentId) {
        return jdbc.sql("SELECT gateway_ref FROM payments WHERE payment_id = :id")
                .param("id", paymentId)
                .query(String.class)
                .optional()
                .orElse(null);
    }

    public void insertProviderReview(int bookingId, int rating, String comment) {
        Map<String, Object> params = new HashMap<>();
        params.put("booking", bookingId);
        params.put("rating", rating);
        params.put("comment", comment);
        jdbc.sql("INSERT INTO provider_reviews (booking_id, rating, comment) VALUES (:booking, :rating, :comment)")
                .params(params)
                .update();
    }

    public void insertServiceReview(int bookingId, int serviceId, int rating, String comment) {
        Map<String, Object> params = new HashMap<>();
        params.put("booking", bookingId);
        params.put("service", serviceId);
        params.put("rating", rating);
        params.put("comment", comment);
        jdbc.sql("""
                        INSERT INTO service_reviews (service_id, booking_id, rating, comment)
                        VALUES (:service, :booking, :rating, :comment)
                        """).params(params).update();
    }

    public int insertComplaint(int bookingId, String subject, String description, int raisedBy) {
        Map<String, Object> params = new HashMap<>();
        params.put("booking", bookingId);
        params.put("subject", subject);
        params.put("description", description);
        params.put("by", raisedBy);
        return jdbc.sql("""
                        INSERT INTO complaints (booking_id, subject, description, raised_by)
                        VALUES (:booking, :subject, :description, :by)
                        RETURNING complaint_id
                        """).params(params).query(Integer.class).single();
    }

    public int expirePendingBookings() {
        return jdbc.sql("SELECT fn_expire_pending_bookings()")
                .query(Integer.class)
                .single();
    }
}
