package com.sevak.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sevak.api.admin.AdminRepository;
import com.sevak.api.admin.dto.LeaderboardEntry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

/** Each test tries to break a business rule and proves the database refuses. */
class DatabaseConstraintsTest extends IntegrationTest {

    private static final String BOOKING_COLUMNS =
            "INSERT INTO bookings (customer_id, provider_id, location_id, scheduled_start, scheduled_end,"
                    + " status, subtotal, discount, total_amount) ";

    @Autowired
    private AdminRepository admin;

    private void assertRejected(String sql, String expectedReason) {
        assertThatThrownBy(() -> jdbc.update(sql))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining(expectedReason);
    }

    /** A booking on the day of seeded booking 4, when provider 1 is already booked 14:00-14:45. */
    private String bookingOnBooking4Day(int providerId, String start, String end, String status) {
        String day =
                "(SELECT CAST(scheduled_start AT TIME ZONE 'Asia/Kolkata' AS date) FROM bookings WHERE booking_id = 4)";
        return BOOKING_COLUMNS + "VALUES (1, " + providerId + ", 1, (" + day + " + time '" + start
                + "') AT TIME ZONE 'Asia/Kolkata', (" + day + " + time '" + end + "') AT TIME ZONE 'Asia/Kolkata', '"
                + status + "', 0, 0, 0)";
    }

    @Test
    void aCustomerUserCannotHaveAProviderProfile() {
        assertRejected("INSERT INTO service_providers (user_id, city_id) VALUES (1, 1)", "fk_providers_user_role");
    }

    @Test
    void aUserRoleCannotChangeWhileASubtypeRowExists() {
        assertRejected("UPDATE users SET role = 'provider' WHERE user_id = 1", "fk_customers_user_role");
    }

    @Test
    void anAdminPermissionMustBeAKnownValue() {
        assertRejected("INSERT INTO admin_permissions VALUES (1, 'DELETE_EVERYTHING')", "admin_permissions");
    }

    @Test
    void aBookingItemCannotUseAnotherServicesVariant() {
        assertRejected(
                "INSERT INTO booking_items (booking_id, item_no, service_id, variant_id, quantity, unit_price)"
                        + " VALUES (4, 9, 1, 3, 1, 100)",
                "fk_item_variant_matches_service");
    }

    @Test
    void aDuplicateAreaInOneCityIsRejected() {
        assertRejected(
                "INSERT INTO areas (city_id, area_name, pincode) VALUES (1, 'Navrangpura', '380009')",
                "uq_areas_city_name");
    }

    @Test
    void aLocationMustHaveCoordinates() {
        assertRejected("INSERT INTO locations (area_id, street) VALUES (1, 'No GPS Road')", "latitude");
    }

    @Test
    void aBookingCannotUseAnotherCustomersAddress() {
        assertRejected(
                BOOKING_COLUMNS + "VALUES (4, 1, 1, '2030-01-01 10:00+05:30', '2030-01-01 11:00+05:30',"
                        + " 'pending', 0, 0, 0)",
                "fk_booking_customer_address");
    }

    @Test
    void overlappingBookingsForOneProviderAreRejected() {
        assertRejected(bookingOnBooking4Day(1, "14:30", "15:00", "pending"), "ex_provider_no_double_booking");
    }

    @Test
    void theSameTimeWithADifferentProviderIsFine() {
        assertThat(jdbc.update(bookingOnBooking4Day(2, "14:30", "15:00", "pending")))
                .isEqualTo(1);
    }

    @Test
    void aBookingMayStartExactlyWhenTheLastOneEnds() {
        assertThat(jdbc.update(bookingOnBooking4Day(1, "14:45", "15:30", "pending")))
                .isEqualTo(1);
    }

    @Test
    void aCancelledBookingFreesItsSlot() {
        jdbc.update("UPDATE bookings SET status = 'cancelled' WHERE booking_id = 4");

        assertThat(jdbc.update(bookingOnBooking4Day(1, "14:30", "15:00", "pending")))
                .isEqualTo(1);
    }

    @Test
    void aBookingMustEndAfterItStarts() {
        assertRejected(bookingOnBooking4Day(1, "16:00", "15:00", "pending"), "chk_booking_time");
    }

    @Test
    void anIllegalStatusChangeIsRejected() {
        assertRejected("UPDATE bookings SET status = 'pending' WHERE booking_id = 1", "Illegal status change");
    }

    @Test
    void totalMustEqualSubtotalMinusDiscount() {
        assertRejected("UPDATE bookings SET total_amount = 1 WHERE booking_id = 4", "chk_booking_total");
    }

    @Test
    void subtotalMustMatchTheItemsWhenTheTransactionCommits() {
        jdbc.update("UPDATE booking_items SET unit_price = 1 WHERE booking_id = 4");

        assertThatThrownBy(() -> jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("does not match its items total");
    }

    @Test
    void aFailedPaymentCanBeRetried() {
        jdbc.update(
                "INSERT INTO payments (booking_id, payment_method, status, amount) VALUES (4, 'UPI', 'failed', 350)");

        assertThat(
                        jdbc.update(
                                "INSERT INTO payments (booking_id, payment_method, status, amount) VALUES (4, 'UPI', 'success', 350)"))
                .isEqualTo(1);
    }

    @Test
    void aBookingCannotHaveTwoSuccessfulPayments() {
        assertRejected(
                "INSERT INTO payments (booking_id, payment_method, status, amount) VALUES (2, 'UPI', 'success', 1)",
                "uq_one_successful_payment");
    }

    @Test
    void aPaymentCannotBeRefundedTwice() {
        jdbc.update("INSERT INTO refunds (payment_id, amount)"
                + " SELECT payment_id, amount FROM payments WHERE booking_id = 2");

        assertRejected(
                "INSERT INTO refunds (payment_id, amount)"
                        + " SELECT payment_id, amount FROM payments WHERE booking_id = 2",
                "uq_one_active_refund");
    }

    @Test
    void processingARefundMarksThePaymentRefunded() {
        jdbc.update("INSERT INTO refunds (payment_id, amount)"
                + " SELECT payment_id, amount FROM payments WHERE booking_id = 2");

        jdbc.update("UPDATE refunds SET status = 'processed'"
                + " WHERE payment_id = (SELECT payment_id FROM payments WHERE booking_id = 2)");

        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE booking_id = 2", String.class))
                .isEqualTo("refunded");
    }

    @Test
    void aPercentageCouponCannotExceed100() {
        assertRejected("UPDATE coupons SET discount_value = 101 WHERE code = 'WELCOME10'", "chk_coupon_percentage");
    }

    @Test
    void aCouponCannotBeUsedBeyondItsLimit() {
        assertRejected(
                "UPDATE coupons SET used_count = usage_limit + 1 WHERE code = 'WELCOME10'", "chk_coupon_usage_limit");
    }

    @Test
    void availabilitySlotsCannotOverlap() {
        assertRejected(
                "INSERT INTO provider_availability (provider_id, day_of_week, start_time, end_time)"
                        + " VALUES (1, 2, '17:00', '19:00')",
                "ex_availability_no_overlap");
    }

    @Test
    void aProviderCanHaveTwoSlotsOnOneDay() {
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM provider_availability WHERE provider_id = 1 AND day_of_week = 1",
                        Integer.class))
                .isEqualTo(2);
    }

    @Test
    void anUnfinishedBookingCannotBeReviewed() {
        assertRejected("INSERT INTO provider_reviews (booking_id, rating) VALUES (6, 5)", "is not completed");
    }

    @Test
    void aServiceReviewMustBeForAServiceInTheBooking() {
        assertRejected(
                "INSERT INTO service_reviews (service_id, booking_id, rating) VALUES (9, 1, 5)",
                "is not part of booking");
    }

    @Test
    void manyGoodReviewsBeatOnePerfectReview() {
        jdbc.update("UPDATE service_providers SET avg_rating = 4.80, review_count = 40 WHERE provider_id = 5");

        List<Integer> ranking = admin.findLeaderboard().stream()
                .map(LeaderboardEntry::providerId)
                .toList();

        assertThat(ranking.get(0)).isEqualTo(5); // 40 reviews at 4.8 outrank 1 review at 5.0
    }

    @Test
    void aProviderWithNoReviewsRanksLast() {
        List<Integer> ranking = admin.findLeaderboard().stream()
                .map(LeaderboardEntry::providerId)
                .toList();

        assertThat(ranking.get(ranking.size() - 1)).isEqualTo(5);
    }
}
