package com.sevak.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** The sp_cancel_booking and sp_confirm_booking procedures, called through the API. */
class BookingProceduresTest extends IntegrationTest {

    private static final int COMPLETED_BOOKING = 1;
    private static final int CONFIRMED_BOOKING = 4;
    private static final int IN_PROGRESS_BOOKING = 5;
    private static final int PENDING_BOOKING_PROVIDER_UNAVAILABLE = 6; // Friday; provider 5 only works Mondays
    private static final int PENDING_BOOKING = 10; // Tuesday 11:00; provider 1 works Tue 09-18
    private static final int UNKNOWN_BOOKING = 9999;

    private int partyOf(String column, int bookingId) {
        Integer id = jdbc.queryForObject(
                "SELECT " + column + " FROM bookings WHERE booking_id = ?", Integer.class, bookingId);
        return id == null ? 0 : id;
    }

    private ResultActions cancel(int bookingId) throws Exception {
        String token = bookingId == UNKNOWN_BOOKING ? customer(1) : customer(partyOf("customer_id", bookingId));
        return mvc.perform(post("/api/v1/bookings/{id}/cancel", bookingId)
                .header(AUTH, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"Changed my mind\"}"));
    }

    private ResultActions confirm(int bookingId) throws Exception {
        String token = bookingId == UNKNOWN_BOOKING ? provider(1) : provider(partyOf("provider_id", bookingId));
        return mvc.perform(post("/api/v1/bookings/{id}/accept", bookingId).header(AUTH, token));
    }

    private String statusOf(int bookingId) {
        return jdbc.queryForObject("SELECT status FROM bookings WHERE booking_id = ?", String.class, bookingId);
    }

    @Test
    void cancellingAnUnpaidBookingNeedsNoRefund() throws Exception {
        cancel(PENDING_BOOKING_PROVIDER_UNAVAILABLE).andExpect(status().isOk());

        assertThat(statusOf(PENDING_BOOKING_PROVIDER_UNAVAILABLE)).isEqualTo("cancelled");
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM cancellations WHERE booking_id = ?",
                        Integer.class,
                        PENDING_BOOKING_PROVIDER_UNAVAILABLE))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refunds", Integer.class))
                .isEqualTo(1); // only the seeded one
    }

    @Test
    void cancellingAPaidBookingStartsARefund() throws Exception {
        jdbc.update(
                "INSERT INTO payments (booking_id, payment_method, status, amount) VALUES (?, 'UPI', 'success', 350)",
                CONFIRMED_BOOKING);

        cancel(CONFIRMED_BOOKING).andExpect(status().isOk());

        // The money is only returned once the refund is processed, so the payment stays 'success' for now.
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM payments WHERE booking_id = ?", String.class, CONFIRMED_BOOKING))
                .isEqualTo("success");
        assertThat(jdbc.queryForObject(
                        "SELECT r.status FROM refunds r JOIN payments p ON p.payment_id = r.payment_id"
                                + " WHERE p.booking_id = ?",
                        String.class,
                        CONFIRMED_BOOKING))
                .isEqualTo("pending");
    }

    @Test
    void cancellingAnInProgressBookingReturns409() throws Exception {
        cancel(IN_PROGRESS_BOOKING).andExpect(status().isConflict());
    }

    @Test
    void confirmingChecksEveryItemNotJustTheFirst() throws Exception {
        // Booking 10 gets a second item for a service provider 1 doesn't offer.
        jdbc.update("INSERT INTO booking_items (booking_id, item_no, service_id, quantity, unit_price)"
                + " VALUES (10, 2, 3, 1, 0)");

        confirm(PENDING_BOOKING)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail", containsString("does not offer service ID 3")));
    }

    @Test
    void confirmingFailsWhenTheJobRunsPastTheEndOfTheSlot() throws Exception {
        jdbc.update("UPDATE bookings SET scheduled_end = scheduled_start + interval '8 hours' WHERE booking_id = 10");

        confirm(PENDING_BOOKING)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail", containsString("is not available")));
    }

    @Test
    void cancellingACompletedBookingReturns409WithoutInternals() throws Exception {
        cancel(COMPLETED_BOOKING)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Booking 1 is completed and cannot be cancelled"))
                .andExpect(jsonPath("$.detail", not(containsString("PL/pgSQL"))));
    }

    @Test
    void cancellingAnUnknownBookingReturns404() throws Exception {
        cancel(UNKNOWN_BOOKING).andExpect(status().isNotFound());
    }

    @Test
    void confirmingAPendingBookingSucceeds() throws Exception {
        confirm(PENDING_BOOKING).andExpect(status().isOk());

        assertThat(statusOf(PENDING_BOOKING)).isEqualTo("confirmed");
    }

    @Test
    void confirmingANonPendingBookingReturns409() throws Exception {
        confirm(CONFIRMED_BOOKING).andExpect(status().isConflict());
    }

    @Test
    void confirmingAnUnknownBookingReturns404() throws Exception {
        confirm(UNKNOWN_BOOKING).andExpect(status().isNotFound());
    }

    @Test
    void confirmingWhenProviderIsUnavailableReturns422() throws Exception {
        confirm(PENDING_BOOKING_PROVIDER_UNAVAILABLE)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail", containsString("is not available")));
    }

    @Test
    void confirmingWhenProviderDoesNotOfferTheServiceReturns422() throws Exception {
        jdbc.update("DELETE FROM provider_services WHERE provider_id = 1 AND service_id = 2");

        confirm(PENDING_BOOKING)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail", containsString("does not offer")));
    }
}
