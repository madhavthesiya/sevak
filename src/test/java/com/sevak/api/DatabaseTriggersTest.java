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
                "SELECT status FROM booking_status_log WHERE booking_id = ? ORDER BY log_time",
                String.class,
                bookingId);
    }

    @Test
    void newReviewRecalculatesTheProvidersRating() {
        assertThat(ratingOfProvider1()).isEqualByComparingTo("5.00"); // booking 1, rated 5

        jdbc.update("INSERT INTO provider_reviews (booking_id, rating) VALUES (4, 3)");

        assertThat(ratingOfProvider1()).isEqualByComparingTo("4.00");
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
        assertThat(statusLog(6)).isEmpty();

        jdbc.update("UPDATE bookings SET status = 'confirmed' WHERE booking_id = 6");

        assertThat(statusLog(6)).containsExactly("confirmed");
    }

    @Test
    void updatingOtherColumnsDoesNotWriteToTheLog() {
        jdbc.update("UPDATE bookings SET special_instructions = 'Ring the bell' WHERE booking_id = 6");

        assertThat(statusLog(6)).isEmpty();
    }
}
