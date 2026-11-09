package com.sevak.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sevak.api.booking.BookingService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** The marketplace from start to finish: book, accept, work, pay, review, complain. */
class BookingFlowTest extends IntegrationTest {

    private static final String TUESDAY_10AM = "2030-01-01T10:00:00+05:30"; // provider 1 works Tue 09-18
    private static final String FAN_ITEM = "{\"serviceId\":1,\"variantId\":1,\"quantity\":1}"; // ₹200, 30 min

    @Autowired
    private BookingService bookings;

    private static String body(String start, String items, String extra) {
        return "{\"providerId\":1,\"locationId\":1,\"scheduledStart\":\"%s\",\"items\":[%s]%s}"
                .formatted(start, items, extra);
    }

    private ResultActions book(int customerId, String json) throws Exception {
        return mvc.perform(post("/api/v1/bookings")
                .header(AUTH, customer(customerId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private int createBooking(String start) throws Exception {
        String response = book(1, body(start, FAN_ITEM, ""))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.bookingId");
    }

    private ResultActions providerAction(int bookingId, String action) throws Exception {
        return mvc.perform(
                post("/api/v1/bookings/{id}/{action}", bookingId, action).header(AUTH, provider(1)));
    }

    private ResultActions post1(String url, String token, String json) throws Exception {
        return mvc.perform(post(url)
                .header(AUTH, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private void addCoupon(String code, String type, int value, String limits) {
        jdbc.update("INSERT INTO coupons (code, discount_type, discount_value, valid_from, valid_to"
                + limits.split("\\|", -1)[0] + ")" + " VALUES ('" + code + "', '" + type + "', " + value
                + ", CURRENT_DATE, CURRENT_DATE + 30" + limits.split("\\|", -1)[1] + ")");
    }

    @Test
    void aBookingUsesTheProvidersPricesAndAddsUpTheDuration() throws Exception {
        String items = "{\"serviceId\":1,\"variantId\":2,\"quantity\":2},{\"serviceId\":2,\"quantity\":1}";

        book(1, body(TUESDAY_10AM, items, ""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.subtotal").value(3300.00)) // 2 x ₹350 variant + ₹2600 provider price
                .andExpect(jsonPath("$.totalAmount").value(3300.00))
                .andExpect(jsonPath("$.scheduledEnd", containsString("T07:00"))); // 10:00 IST + 150 min = 07:00 UTC
    }

    @Test
    void theFirstStatusIsLoggedWithWhoPlacedIt() throws Exception {
        int id = createBooking(TUESDAY_10AM);

        assertThat(jdbc.queryForObject(
                        "SELECT changed_by FROM booking_status_log WHERE booking_id = ?", Integer.class, id))
                .isEqualTo(1);
    }

    @Test
    void aSavedPriceDoesNotChangeWhenTheProviderChangesTheirs() throws Exception {
        int id = createBooking(TUESDAY_10AM);

        jdbc.update("UPDATE service_variants SET price = 9999 WHERE variant_id = 1");

        assertThat(jdbc.queryForObject(
                        "SELECT unit_price FROM booking_items WHERE booking_id = ?", BigDecimal.class, id))
                .isEqualByComparingTo("200.00");
    }

    @Test
    void aTakenSlotReturns409() throws Exception {
        createBooking(TUESDAY_10AM);

        book(3, body("2030-01-01T10:15:00+05:30", FAN_ITEM, ""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:sevak:error:slot-taken"));
    }

    @Test
    void anAddressThatIsNotYoursIsRejected() throws Exception {
        book(4, body(TUESDAY_10AM, FAN_ITEM, "")).andExpect(status().isUnprocessableContent());
    }

    @Test
    void aRemovedAddressCannotBeBookedAt() throws Exception {
        jdbc.update("UPDATE customer_addresses SET is_active = FALSE WHERE customer_id = 1 AND location_id = 1");

        book(1, body(TUESDAY_10AM, FAN_ITEM, "")).andExpect(status().isUnprocessableContent());
    }

    @Test
    void aProviderOutsideTheAreaIsRejected() throws Exception {
        jdbc.update("DELETE FROM provider_service_areas WHERE provider_id = 1");

        book(1, body(TUESDAY_10AM, FAN_ITEM, ""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail", containsString("does not work in that area")));
    }

    @Test
    void anUnverifiedProviderCannotBeBooked() throws Exception {
        jdbc.update("UPDATE service_providers SET verification_status = 'pending' WHERE provider_id = 1");

        book(1, body(TUESDAY_10AM, FAN_ITEM, "")).andExpect(status().isUnprocessableContent());
    }

    @Test
    void aServiceTheProviderDoesNotOfferIsRejected() throws Exception {
        book(1, body(TUESDAY_10AM, "{\"serviceId\":3,\"quantity\":1}", ""))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void anotherServicesVariantIsRejected() throws Exception {
        book(1, body(TUESDAY_10AM, "{\"serviceId\":1,\"variantId\":3,\"quantity\":1}", ""))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void aTimeOutsideTheProvidersHoursIsRejected() throws Exception {
        book(1, body("2030-01-01T20:00:00+05:30", FAN_ITEM, ""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail", containsString("not available")));
    }

    @Test
    void aTimeInThePastIsRejected() throws Exception {
        book(1, body("2020-01-07T10:00:00+05:30", FAN_ITEM, "")).andExpect(status().isBadRequest());
    }

    @Test
    void aBookingNeedsAtLeastOneItem() throws Exception {
        book(1, body(TUESDAY_10AM, "", "")).andExpect(status().isBadRequest());
    }

    @Test
    void aPercentageCouponIsAppliedAndCapped() throws Exception {
        addCoupon("HALF", "percentage", 50, ", max_discount|, 40");

        book(1, body(TUESDAY_10AM, FAN_ITEM, ",\"couponCode\":\"half\""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.discount").value(40.00))
                .andExpect(jsonPath("$.totalAmount").value(160.00));

        assertThat(jdbc.queryForObject("SELECT used_count FROM coupons WHERE code = 'HALF'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void aFlatCouponNeverMakesTheTotalNegative() throws Exception {
        addCoupon("HUGE", "flat", 99999, "|");

        book(1, body(TUESDAY_10AM, FAN_ITEM, ",\"couponCode\":\"HUGE\""))
                .andExpect(jsonPath("$.totalAmount").value(0.0));
    }

    @Test
    void aCustomerCanUseACouponOnlyOnce() throws Exception {
        addCoupon("ONCE", "flat", 10, "|");
        book(1, body(TUESDAY_10AM, FAN_ITEM, ",\"couponCode\":\"ONCE\"")).andExpect(status().isCreated());

        book(1, body("2030-01-08T10:00:00+05:30", FAN_ITEM, ",\"couponCode\":\"ONCE\""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("urn:sevak:error:coupon-invalid"));
    }

    @Test
    void aCouponBelowItsMinimumOrUsedUpIsRejected() throws Exception {
        addCoupon("BIGORDER", "flat", 10, ", min_order|, 1000");
        addCoupon("GONE", "flat", 10, ", usage_limit, used_count|, 1, 1");

        book(1, body(TUESDAY_10AM, FAN_ITEM, ",\"couponCode\":\"BIGORDER\""))
                .andExpect(status().isUnprocessableContent());
        book(1, body("2030-01-08T10:00:00+05:30", FAN_ITEM, ",\"couponCode\":\"GONE\""))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void aRetryWithTheSameKeyReturnsTheSameBooking() throws Exception {
        String json = body(TUESDAY_10AM, FAN_ITEM, "");
        String first = mvc.perform(post("/api/v1/bookings")
                        .header(AUTH, customer(1))
                        .header("Idempotency-Key", "abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        int id = JsonPath.read(first, "$.bookingId");

        mvc.perform(post("/api/v1/bookings")
                        .header(AUTH, customer(1))
                        .header("Idempotency-Key", "abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(id));
    }

    @Test
    void reusingAKeyForADifferentRequestIsRefused() throws Exception {
        mvc.perform(post("/api/v1/bookings")
                        .header(AUTH, customer(1))
                        .header("Idempotency-Key", "abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(TUESDAY_10AM, FAN_ITEM, "")))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/bookings")
                        .header(AUTH, customer(1))
                        .header("Idempotency-Key", "abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("2030-01-08T10:00:00+05:30", FAN_ITEM, "")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:sevak:error:idempotency-conflict"));
    }

    @Test
    void theWholeJourneyWorks() throws Exception {
        int id = createBooking(TUESDAY_10AM);

        providerAction(id, "accept").andExpect(jsonPath("$.status").value("confirmed"));
        providerAction(id, "start").andExpect(jsonPath("$.status").value("in_progress"));
        providerAction(id, "complete").andExpect(jsonPath("$.status").value("completed"));
        post1("/api/v1/bookings/" + id + "/payments", customer(1), "{\"paymentMethod\":\"UPI\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.gatewayRef").exists());
        post1(
                        "/api/v1/bookings/" + id + "/review",
                        customer(1),
                        "{\"rating\":1,\"serviceReviews\":[{\"serviceId\":1,\"rating\":2}]}")
                .andExpect(status().isCreated());

        assertThat(jdbc.queryForObject(
                        "SELECT review_count FROM service_providers WHERE provider_id = 1", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForList(
                        "SELECT changed_by FROM booking_status_log WHERE booking_id = ? ORDER BY log_id",
                        Integer.class,
                        id))
                .containsExactly(1, 8, 8, 8);
        mvc.perform(get("/api/v1/me/bookings/" + id).header(AUTH, customer(1)))
                .andExpect(jsonPath("$.timeline.length()").value(4))
                .andExpect(jsonPath("$.booking.status").value("completed"));
    }

    @Test
    void aJobCannotBeCompletedBeforeItStarts() throws Exception {
        providerAction(createBooking(TUESDAY_10AM), "complete").andExpect(status().isConflict());
    }

    @Test
    void aJobCannotStartBeforeItIsAccepted() throws Exception {
        providerAction(createBooking(TUESDAY_10AM), "start").andExpect(status().isConflict());
    }

    @Test
    void aProviderCanDeclineOnlyWhilePending() throws Exception {
        int id = createBooking(TUESDAY_10AM);
        providerAction(id, "decline").andExpect(jsonPath("$.status").value("cancelled"));

        providerAction(id, "decline").andExpect(status().isConflict());
    }

    @Test
    void aCustomerCanCancelUntilTwoHoursBefore() throws Exception {
        int id = createBooking(TUESDAY_10AM);
        jdbc.update(
                "UPDATE bookings SET scheduled_start = now() + interval '1 hour', scheduled_end = now() + interval '90 minutes'"
                        + " WHERE booking_id = ?",
                id);

        post1("/api/v1/bookings/" + id + "/cancel", customer(1), "{\"reason\":\"Too late\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail", containsString("2 hours")));
        post1("/api/v1/bookings/" + id + "/cancel", provider(1), "{\"reason\":\"Sick\"}")
                .andExpect(status().isOk());
    }

    @Test
    void aProviderCannotCancelAfterTheStart() throws Exception {
        int id = createBooking(TUESDAY_10AM);
        jdbc.update(
                "UPDATE bookings SET scheduled_start = now() - interval '1 hour', scheduled_end = now()"
                        + " WHERE booking_id = ?",
                id);

        post1("/api/v1/bookings/" + id + "/cancel", provider(1), "{\"reason\":\"Late\"}")
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void cancellingRecordsWhoDidItAndReleasesTheCoupon() throws Exception {
        addCoupon("BACK", "flat", 10, "|");
        String response = book(1, body(TUESDAY_10AM, FAN_ITEM, ",\"couponCode\":\"BACK\""))
                .andReturn()
                .getResponse()
                .getContentAsString();
        int id = JsonPath.read(response, "$.bookingId");

        post1("/api/v1/bookings/" + id + "/cancel", customer(1), "{\"reason\":\"Changed plans\"}")
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                        "SELECT cancelled_by FROM cancellations WHERE booking_id = ?", Integer.class, id))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT used_count FROM coupons WHERE code = 'BACK'", Integer.class))
                .isZero();
        book(1, body("2030-01-08T10:00:00+05:30", FAN_ITEM, ",\"couponCode\":\"BACK\""))
                .andExpect(status().isCreated());
    }

    @Test
    void aFailedPaymentCanBeRetriedOnce() throws Exception {
        int id = createBooking(TUESDAY_10AM);
        String url = "/api/v1/bookings/" + id + "/payments";

        post1(url, customer(1), "{\"paymentMethod\":\"UPI\",\"simulate\":\"failure\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("failed"));
        post1(url, customer(1), "{\"paymentMethod\":\"UPI\"}")
                .andExpect(jsonPath("$.status").value("success"));
        post1(url, customer(1), "{\"paymentMethod\":\"UPI\"}").andExpect(status().isConflict());
    }

    @Test
    void aCancelledBookingCannotBePaid() throws Exception {
        int id = createBooking(TUESDAY_10AM);
        providerAction(id, "decline");

        post1("/api/v1/bookings/" + id + "/payments", customer(1), "{\"paymentMethod\":\"UPI\"}")
                .andExpect(status().isConflict());
    }

    @Test
    void anUnfinishedBookingCannotBeReviewed() throws Exception {
        int id = createBooking(TUESDAY_10AM);

        post1("/api/v1/bookings/" + id + "/review", customer(1), "{\"rating\":5}")
                .andExpect(status().isConflict());
    }

    @Test
    void aBookingCanBeReviewedOnlyOnce() throws Exception {
        post1("/api/v1/bookings/1/review", customer(1), "{\"rating\":5}").andExpect(status().isConflict());
    }

    @Test
    void aReviewForAServiceNotInTheBookingIsRejected() throws Exception {
        jdbc.update("DELETE FROM service_reviews WHERE booking_id = 1");
        jdbc.update("DELETE FROM provider_reviews WHERE booking_id = 1");

        post1(
                        "/api/v1/bookings/1/review",
                        customer(1),
                        "{\"rating\":5,\"serviceReviews\":[{\"serviceId\":9,\"rating\":5}]}")
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void bothSidesCanComplain() throws Exception {
        post1("/api/v1/bookings/1/complaints", customer(1), "{\"subject\":\"Late\"}")
                .andExpect(status().isCreated());
        post1("/api/v1/bookings/1/complaints", provider(1), "{\"subject\":\"Rude\"}")
                .andExpect(status().isCreated());

        assertThat(jdbc.queryForList(
                        "SELECT raised_by FROM complaints WHERE booking_id = 1 ORDER BY complaint_id", Integer.class))
                .containsExactly(1, 8);
    }

    @Test
    void aProviderSeesTheStreetOnlyAfterAccepting() throws Exception {
        mvc.perform(get("/api/v1/provider/jobs?status=pending").header(AUTH, provider(1)))
                .andExpect(jsonPath("$.data[0].street").doesNotExist())
                .andExpect(jsonPath("$.data[0].areaName").exists());
        mvc.perform(get("/api/v1/provider/jobs?status=confirmed").header(AUTH, provider(1)))
                .andExpect(jsonPath("$.data[0].street").exists());
    }

    @Test
    void addressesCanBeAddedAndRemoved() throws Exception {
        post1(
                        "/api/v1/me/addresses",
                        customer(4),
                        "{\"areaId\":5,\"street\":\"1 New Road\",\"latitude\":12.1,\"longitude\":77.1,\"label\":\"Gym\",\"houseNo\":\"7\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2));
        Integer location =
                jdbc.queryForObject("SELECT location_id FROM locations WHERE street = '1 New Road'", Integer.class);

        mvc.perform(delete("/api/v1/me/addresses/" + location).header(AUTH, customer(4)))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/me/addresses/" + location).header(AUTH, customer(4)))
                .andExpect(status().isNotFound());
    }

    @Test
    void anAddressInAnUnknownAreaIsRejected() throws Exception {
        post1(
                        "/api/v1/me/addresses",
                        customer(4),
                        "{\"areaId\":999,\"street\":\"X\",\"latitude\":1.5,\"longitude\":1.5}")
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void verifyingAProviderMakesThemSearchable() throws Exception {
        jdbc.update("UPDATE service_providers SET verification_status = 'pending' WHERE provider_id = 1");
        jdbc.update("UPDATE provider_documents SET status = 'pending', verified_by = NULL WHERE provider_id = 1");
        mvc.perform(get("/api/v1/providers?service=fan"))
                .andExpect(jsonPath("$.total").value(0));

        mvc.perform(patch("/api/v1/admin/providers/1/verification")
                        .header(AUTH, admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"verified\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/providers?service=fan"))
                .andExpect(jsonPath("$.total").value(1));
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM provider_documents WHERE provider_id = 1 AND verified_by = 1",
                        Integer.class))
                .isEqualTo(2);
    }

    @Test
    void resolvingAComplaintStampsTheTime() throws Exception {
        mvc.perform(patch("/api/v1/admin/complaints/3")
                        .header(AUTH, admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"resolved\",\"assignedAdminId\":1,\"resolutionNotes\":\"Repainted\"}"))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                        "SELECT resolved_at IS NOT NULL FROM complaints WHERE complaint_id = 3", Boolean.class))
                .isTrue();
    }

    @Test
    void unknownAdminActionsReturn404() throws Exception {
        mvc.perform(patch("/api/v1/admin/complaints/999")
                        .header(AUTH, admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"closed\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/admin/providers/999/verification")
                        .header(AUTH, admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"verified\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/admin/refunds/999/process").header(AUTH, admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void processingARefundOnlyWorksOnce() throws Exception {
        jdbc.update(
                "INSERT INTO refunds (payment_id, amount) SELECT payment_id, amount FROM payments WHERE booking_id = 2");
        Integer refund = jdbc.queryForObject(
                "SELECT r.refund_id FROM refunds r JOIN payments p USING (payment_id) WHERE p.booking_id = 2",
                Integer.class);

        mvc.perform(post("/api/v1/admin/refunds/" + refund + "/process").header(AUTH, admin()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/refunds/" + refund + "/process").header(AUTH, admin()))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE booking_id = 2", String.class))
                .isEqualTo("refunded");
    }

    @Test
    void anUnansweredRequestExpiresAfter24Hours() {
        jdbc.update("UPDATE bookings SET created_at = now() - interval '25 hours' WHERE booking_id = 6");

        assertThat(bookings.expirePendingBookings()).isEqualTo(1);

        assertThat(jdbc.queryForObject("SELECT status FROM bookings WHERE booking_id = 6", String.class))
                .isEqualTo("cancelled");
        assertThat(jdbc.queryForObject("SELECT status FROM bookings WHERE booking_id = 10", String.class))
                .isEqualTo("pending");
    }

    @Test
    void anUnansweredRequestExpiresAtItsStartTime() {
        jdbc.update(
                "UPDATE bookings SET scheduled_start = now() - interval '1 minute', scheduled_end = now() WHERE booking_id = 10");

        assertThat(bookings.expirePendingBookings()).isEqualTo(1);
    }

    @Test
    void anExpiredBookingFreesItsSlot() throws Exception {
        int id = createBooking(TUESDAY_10AM);
        jdbc.update("UPDATE bookings SET created_at = now() - interval '25 hours' WHERE booking_id = ?", id);
        bookings.expirePendingBookings();

        book(3, body(TUESDAY_10AM, FAN_ITEM, "")).andExpect(status().isCreated());
    }
}
