package com.sevak.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** The sp_cancel_booking and sp_confirm_booking procedures, called through the API. */
class BookingProceduresTest extends IntegrationTest {

    private static final int COMPLETED_BOOKING = 1;
    private static final int CONFIRMED_BOOKING = 4;
    private static final int IN_PROGRESS_BOOKING = 5;
    private static final int PENDING_BOOKING_PROVIDER_UNAVAILABLE = 6; // Friday; provider 5 only works Mondays
    private static final int PENDING_BOOKING = 10; // Tuesday 11:00; provider 1 works Tue 09-18
    private static final int UNKNOWN_BOOKING = 9999;

    private ResultActions cancel(int bookingId) throws Exception {
        return mvc.perform(post("/api/bookings/{id}/cancel", bookingId)
                .header(API_KEY_HEADER, API_KEY)
                .param("reason", "Changed my mind"));
    }

    private ResultActions confirm(int bookingId) throws Exception {
        return mvc.perform(post("/api/bookings/{id}/confirm", bookingId).header(API_KEY_HEADER, API_KEY));
    }

    private String statusOf(int bookingId) {
        return jdbc.queryForObject("SELECT status FROM bookings WHERE booking_id = ?", String.class, bookingId);
    }

    @Test
    void cancellingAnUnpaidBookingNeedsNoRefund() throws Exception {
        cancel(PENDING_BOOKING_PROVIDER_UNAVAILABLE).andExpect(status().isOk());

        assertThat(statusOf(PENDING_BOOKING_PROVIDER_UNAVAILABLE)).isEqualTo("cancelled");
        assertThat(jdbc.queryForObject(
                        "SELECT refund_status FROM cancellations WHERE booking_id = ?",
                        String.class,
                        PENDING_BOOKING_PROVIDER_UNAVAILABLE))
                .isEqualTo("not_applicable");
    }

    @Test
    void cancellingAPaidBookingStartsARefund() throws Exception {
        jdbc.update("UPDATE payments SET status = 'success' WHERE booking_id = ?", IN_PROGRESS_BOOKING);

        cancel(IN_PROGRESS_BOOKING).andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                        "SELECT status FROM payments WHERE booking_id = ?", String.class, IN_PROGRESS_BOOKING))
                .isEqualTo("refunded");
        assertThat(jdbc.queryForObject(
                        "SELECT refund_status FROM cancellations WHERE booking_id = ?",
                        String.class,
                        IN_PROGRESS_BOOKING))
                .isEqualTo("pending");
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
