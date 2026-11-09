package com.sevak.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The triggers keep derived data and the audit trail correct without any application code. */
class DatabaseTriggersTest extends IntegrationTest {

    private BigDecimal ratingOfProvider1() {
        return jdbc.queryForObject("SELECT avg_rating FROM service_providers WHERE provider_id = 1", BigDecimal.class);
    }

    private List<String> statusLog(int bookingId) {
        return jdbc.queryForList(
                "SELECT new_status FROM booking_status_log WHERE booking_id = ? ORDER BY log_id",
                String.class,
                bookingId);
    }

    @Test
    void newReviewRecalculatesTheProvidersRating() {
        assertThat(ratingOfProvider1()).isEqualByComparingTo("5.00"); // booking 1, rated 5
        jdbc.update("UPDATE bookings SET status = 'in_progress' WHERE booking_id = 4");
        jdbc.update("UPDATE bookings SET status = 'completed' WHERE booking_id = 4");

        jdbc.update("INSERT INTO provider_reviews (booking_id, rating) VALUES (4, 3)");

        assertThat(ratingOfProvider1()).isEqualByComparingTo("4.00");
        assertThat(jdbc.queryForObject(
                        "SELECT review_count FROM service_providers WHERE provider_id = 1", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void editedReviewRecalculatesTheProvidersRating() {
        jdbc.update("UPDATE provider_reviews SET rating = 1 WHERE booking_id = 1");

        assertThat(ratingOfProvider1()).isEqualByComparingTo("1.00");
    }

    @Test
    void deletingTheLastReviewClearsTheRating() {
        jdbc.update("DELETE FROM provider_reviews WHERE booking_id = 1");

        assertThat(ratingOfProvider1()).isNull();
    }

    @Test
    void statusChangeIsWrittenToTheLog() {
        assertThat(statusLog(6)).containsExactly("pending");

        jdbc.update("UPDATE bookings SET status = 'confirmed' WHERE booking_id = 6");

        assertThat(statusLog(6)).containsExactly("pending", "confirmed");
    }

    @Test
    void twoStatusChangesInOneTransactionAreBothLogged() {
        jdbc.update("UPDATE bookings SET status = 'confirmed' WHERE booking_id = 6");
        jdbc.update("UPDATE bookings SET status = 'in_progress' WHERE booking_id = 6");

        assertThat(statusLog(6)).containsExactly("pending", "confirmed", "in_progress");
    }

    @Test
    void aNewBookingLogsItsFirstStatus() {
        Integer id = jdbc.queryForObject("""
                INSERT INTO bookings (customer_id, provider_id, location_id, scheduled_start, scheduled_end,
                                      status, subtotal, discount, total_amount)
                VALUES (1, 1, 1, '2030-01-01 10:00+05:30', '2030-01-01 11:00+05:30', 'pending', 0, 0, 0)
                RETURNING booking_id
                """, Integer.class);

        assertThat(statusLog(id)).containsExactly("pending");
    }

    @Test
    void theLogRecordsWhoMadeTheChange() {
        jdbc.queryForObject("SELECT set_config('sevak.user_id', '14', true)", String.class);

        jdbc.update("UPDATE bookings SET status = 'confirmed' WHERE booking_id = 6");

        assertThat(jdbc.queryForObject(
                        "SELECT changed_by FROM booking_status_log WHERE booking_id = 6 ORDER BY log_id DESC LIMIT 1",
                        Integer.class))
                .isEqualTo(14);
    }

    @Test
    void updatingOtherColumnsDoesNotWriteToTheLog() {
        jdbc.update("UPDATE bookings SET special_instructions = 'Ring the bell' WHERE booking_id = 6");

        assertThat(statusLog(6)).containsExactly("pending");
    }
}
