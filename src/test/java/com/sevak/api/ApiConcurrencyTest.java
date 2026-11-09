package com.sevak.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.sevak.api.admin.DemoResetService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Two people hitting the real API at the same instant. These tests commit, then clean up. */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ApiConcurrencyTest extends IntegrationTest {

    private static final int ROUNDS = 15;
    private static final LocalDate FIRST_TUESDAY = LocalDate.of(2031, 1, 7);

    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    @Autowired
    private DemoResetService demoReset;

    @AfterEach
    void cleanUp() {
        pool.shutdownNow();
        String mine = "SELECT booking_id FROM bookings WHERE scheduled_start >= '2031-01-01'";
        jdbc.update("DELETE FROM cancellations WHERE booking_id IN (" + mine + ")");
        jdbc.update("DELETE FROM bookings WHERE scheduled_start >= '2031-01-01'");
        jdbc.update("DELETE FROM coupons WHERE code = 'LASTONE'");
    }

    private static String body(LocalDate date, String extra) {
        return "{\"providerId\":1,\"locationId\":1,\"scheduledStart\":\"%sT10:00:00+05:30\",\"items\":[{\"serviceId\":1,\"variantId\":1,\"quantity\":1}]%s}"
                .formatted(date, extra);
    }

    private int postBooking(String token, String json, String key) throws Exception {
        var request = post("/api/v1/bookings")
                .header(AUTH, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    private List<Integer> race(Callable<Integer> first, Callable<Integer> second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (Callable<Integer> task : List.of(first, second)) {
            futures.add(pool.submit(() -> {
                start.await();
                return task.call();
            }));
        }
        start.countDown();
        List<Integer> results = new ArrayList<>();
        for (Future<Integer> future : futures) {
            results.add(future.get());
        }
        return results;
    }

    private int bookingCount() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM bookings WHERE scheduled_start >= '2031-01-01'", Integer.class);
    }

    @Test
    void twoCustomersForTheSameSlotOneGetsIt() throws Exception {
        String first = customer(1);
        String second = customer(3);
        for (int round = 0; round < ROUNDS; round++) {
            String json = body(FIRST_TUESDAY.plusWeeks(round), "");

            assertThat(race(() -> postBooking(first, json, null), () -> postBooking(second, json, null)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(bookingCount()).isEqualTo(ROUNDS);
    }

    @Test
    void theSameRetryAtTheSameTimeCreatesOneBooking() throws Exception {
        String token = customer(1);
        for (int round = 0; round < ROUNDS; round++) {
            String json = body(FIRST_TUESDAY.plusWeeks(round), "");
            String key = "retry-" + round;

            assertThat(race(() -> postBooking(token, json, key), () -> postBooking(token, json, key)))
                    .containsExactlyInAnyOrder(201, 200);
        }
        assertThat(bookingCount()).isEqualTo(ROUNDS);
    }

    @Test
    void theLastCouponGoesToOneCustomer() throws Exception {
        String first = customer(1);
        String second = customer(3);
        for (int round = 0; round < ROUNDS; round++) {
            jdbc.update("DELETE FROM bookings WHERE scheduled_start >= '2031-01-01'");
            jdbc.update("DELETE FROM coupons WHERE code = 'LASTONE'");
            jdbc.update("INSERT INTO coupons (code, discount_type, discount_value, usage_limit, valid_from, valid_to)"
                    + " VALUES ('LASTONE', 'flat', 10, 1, CURRENT_DATE, CURRENT_DATE + 30)");
            LocalDate date = FIRST_TUESDAY.plusWeeks(round);
            String coupon = ",\"couponCode\":\"LASTONE\"";

            // Different hours, so the only thing they can clash on is the coupon.
            String json1 = body(date, coupon);
            String json2 = body(date, coupon).replace("T10:00", "T12:00");
            assertThat(race(() -> postBooking(first, json1, null), () -> postBooking(second, json2, null)))
                    .containsExactlyInAnyOrder(201, 422);
            assertThat(jdbc.queryForObject("SELECT used_count FROM coupons WHERE code = 'LASTONE'", Integer.class))
                    .isEqualTo(1);
        }
    }

    @Test
    void aFailedBookingDoesNotUseUpACoupon() throws Exception {
        jdbc.update("INSERT INTO coupons (code, discount_type, discount_value, usage_limit, valid_from, valid_to)"
                + " VALUES ('LASTONE', 'flat', 10, 5, CURRENT_DATE, CURRENT_DATE + 30)");
        postBooking(customer(1), body(FIRST_TUESDAY, ""), null);

        // The slot is taken, so this fails after the coupon was already claimed; everything must roll back.
        assertThat(postBooking(customer(3), body(FIRST_TUESDAY, ",\"couponCode\":\"LASTONE\""), null))
                .isEqualTo(409);

        assertThat(jdbc.queryForObject("SELECT used_count FROM coupons WHERE code = 'LASTONE'", Integer.class))
                .isZero();
    }

    @Test
    void theNightlyResetRestoresTheDemoData() {
        jdbc.update("UPDATE service_providers SET bio = 'Vandalised' WHERE provider_id = 1");
        jdbc.update("DELETE FROM complaints");

        demoReset.reset();

        assertThat(jdbc.queryForObject("SELECT bio FROM service_providers WHERE provider_id = 1", String.class))
                .isNotEqualTo("Vandalised");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM complaints", Integer.class))
                .isEqualTo(3);
    }
}
