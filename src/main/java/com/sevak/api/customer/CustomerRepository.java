package com.sevak.api.customer;

import com.sevak.api.customer.dto.BookingHeader;
import com.sevak.api.customer.dto.BookingLine;
import com.sevak.api.customer.dto.BookingSummary;
import com.sevak.api.customer.dto.PaymentRecord;
import com.sevak.api.customer.dto.SavedAddress;
import com.sevak.api.customer.dto.SpendingSummary;
import com.sevak.api.customer.dto.StatusChange;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerRepository {

    private final JdbcClient jdbc;

    public CustomerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public boolean exists(int customerId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM customers WHERE customer_id = :id)")
                .param("id", customerId)
                .query(Boolean.class)
                .single();
    }

    public List<BookingSummary> findBookings(int customerId, int limit, long offset) {
        return jdbc.sql("""
                        SELECT b.booking_id, u.full_name AS provider_name, b.scheduled_start, b.scheduled_end,
                               b.status, b.total_amount
                        FROM bookings b
                        JOIN service_providers sp ON sp.provider_id = b.provider_id
                        JOIN users u ON u.user_id = sp.user_id
                        WHERE b.customer_id = :id
                        ORDER BY b.scheduled_start DESC, b.booking_id DESC
                        LIMIT :limit OFFSET :offset
                        """)
                .param("id", customerId)
                .param("limit", limit)
                .param("offset", offset)
                .query(BookingSummary.class)
                .list();
    }

    public long countBookings(int customerId) {
        return jdbc.sql("SELECT COUNT(*) FROM bookings WHERE customer_id = :id")
                .param("id", customerId)
                .query(Long.class)
                .single();
    }

    /** Filters on the customer as well, so a customer can only read their own booking. */
    public Optional<BookingHeader> findBookingHeader(int customerId, int bookingId) {
        return jdbc.sql("""
                        SELECT b.booking_id, u.full_name AS provider_name, b.scheduled_start, b.scheduled_end,
                               b.status, b.subtotal, b.discount, b.total_amount, c.code AS coupon_code,
                               b.special_instructions, ca.house_no, loc.street, ar.area_name, ci.city_name
                        FROM bookings b
                        JOIN service_providers sp   ON sp.provider_id = b.provider_id
                        JOIN users u                ON u.user_id = sp.user_id
                        JOIN customer_addresses ca  ON ca.customer_id = b.customer_id AND ca.location_id = b.location_id
                        JOIN locations loc          ON loc.location_id = b.location_id
                        JOIN areas ar               ON ar.area_id = loc.area_id
                        JOIN cities ci              ON ci.city_id = ar.city_id
                        LEFT JOIN coupons c         ON c.coupon_id = b.coupon_id
                        WHERE b.booking_id = :booking AND b.customer_id = :customer
                        """)
                .param("booking", bookingId)
                .param("customer", customerId)
                .query(BookingHeader.class)
                .optional();
    }

    public List<BookingLine> findBookingLines(int bookingId) {
        return jdbc.sql("""
                        SELECT bi.item_no, s.service_name, sv.variant_name, bi.quantity, bi.unit_price,
                               bi.quantity * bi.unit_price AS line_total
                        FROM booking_items bi
                        JOIN services s ON s.service_id = bi.service_id
                        LEFT JOIN service_variants sv ON sv.variant_id = bi.variant_id
                        WHERE bi.booking_id = :id
                        ORDER BY bi.item_no
                        """).param("id", bookingId).query(BookingLine.class).list();
    }

    public List<StatusChange> findTimeline(int bookingId) {
        return jdbc.sql("""
                        SELECT old_status, new_status, changed_at, remarks
                        FROM booking_status_log
                        WHERE booking_id = :id
                        ORDER BY log_id
                        """).param("id", bookingId).query(StatusChange.class).list();
    }

    public List<PaymentRecord> findPayments(int customerId, int limit, long offset) {
        return jdbc.sql("""
                        SELECT b.booking_id, p.payment_method, p.status, p.amount, p.gateway_ref, p.paid_at,
                               p.failure_reason
                        FROM payments p
                        JOIN bookings b ON b.booking_id = p.booking_id
                        WHERE b.customer_id = :id
                        ORDER BY p.paid_at DESC NULLS LAST, p.payment_id DESC
                        LIMIT :limit OFFSET :offset
                        """)
                .param("id", customerId)
                .param("limit", limit)
                .param("offset", offset)
                .query(PaymentRecord.class)
                .list();
    }

    public long countPayments(int customerId) {
        return jdbc.sql("""
                        SELECT COUNT(*) FROM payments p
                        JOIN bookings b ON b.booking_id = p.booking_id
                        WHERE b.customer_id = :id
                        """).param("id", customerId).query(Long.class).single();
    }

    public SpendingSummary findSpending(int customerId) {
        return jdbc.sql("""
                        WITH paid AS (
                            SELECT booking_id,
                                   COALESCE(SUM(amount) FILTER (WHERE status = 'success'), 0)  AS paid,
                                   COALESCE(SUM(amount) FILTER (WHERE status = 'refunded'), 0) AS refunded
                            FROM payments
                            GROUP BY booking_id
                        )
                        SELECT
                            COUNT(*)                                       AS total_bookings,
                            COUNT(*) FILTER (WHERE b.status = 'completed') AS completed,
                            COUNT(*) FILTER (WHERE b.status = 'cancelled') AS cancelled,
                            COUNT(*) FILTER (WHERE b.status IN ('pending', 'confirmed', 'in_progress')) AS pending,
                            COALESCE(SUM(p.paid), 0)                       AS paid,
                            COALESCE(SUM(b.total_amount) FILTER (
                                WHERE b.status IN ('pending', 'confirmed', 'in_progress')
                                  AND COALESCE(p.paid, 0) = 0), 0)         AS upcoming,
                            COALESCE(SUM(p.refunded), 0)                   AS refunded
                        FROM bookings b
                        LEFT JOIN paid p ON p.booking_id = b.booking_id
                        WHERE b.customer_id = :id
                        """)
                .param("id", customerId)
                .query(SpendingSummary.class)
                .single();
    }

    public List<SavedAddress> findAddresses(int customerId) {
        return jdbc.sql("""
                        SELECT loc.location_id, ca.label, ca.house_no, loc.street, loc.landmark,
                               ar.area_name, ar.pincode, ci.city_name
                        FROM customer_addresses ca
                        JOIN locations loc ON loc.location_id = ca.location_id
                        JOIN areas ar      ON ar.area_id = loc.area_id
                        JOIN cities ci     ON ci.city_id = ar.city_id
                        WHERE ca.customer_id = :id AND ca.is_active
                        ORDER BY ca.label, loc.location_id
                        """).param("id", customerId).query(SavedAddress.class).list();
    }
}
