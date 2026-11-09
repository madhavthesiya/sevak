package com.sevak.api;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Two connections doing conflicting work at the same moment. These tests really commit, because
 * locks only matter between separate transactions, so they clean up after themselves.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BookingConcurrencyTest extends IntegrationTest {

    private static final int ROUNDS = 20;
    private static final LocalDate FIRST_TUESDAY = LocalDate.of(2027, 1, 5);

    private final List<Integer> createdBookings = new ArrayList<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    @Autowired
    private TransactionTemplate tx;

    @AfterEach
    void removeCommittedData() {
        pool.shutdownNow();
        for (int id : createdBookings) {
            jdbc.update("DELETE FROM cancellations WHERE booking_id = ?", id);
            jdbc.update("DELETE FROM bookings WHERE booking_id = ?", id);
        }
    }

    /** Commits a pending 30-minute booking for provider 1 on a Tuesday, inside their 09:00-18:00 slot. */
    private int createBooking(int customerId, LocalDate date) {
        Integer id = tx.execute(status -> {
            Integer bookingId = jdbc.queryForObject("""
                    INSERT INTO bookings (customer_id, provider_id, location_id, scheduled_start, scheduled_end,
                                          status, subtotal, discount, total_amount)
                    VALUES (?, 1, 1, (?::date + time '10:00') AT TIME ZONE 'Asia/Kolkata',
                                     (?::date + time '10:30') AT TIME ZONE 'Asia/Kolkata',
                            'pending', 200, 0, 200)
                    RETURNING booking_id
                    """, Integer.class, customerId, date.toString(), date.toString());
            jdbc.update(
                    "INSERT INTO booking_items (booking_id, item_no, service_id, variant_id, quantity, unit_price)"
                            + " VALUES (?, 1, 1, 1, 1, 200)",
                    bookingId);
            return bookingId;
        });
        createdBookings.add(id);
        return id;
    }

    /** Runs both tasks at the same instant and returns true for each one that succeeded. */
    private List<Boolean> race(Runnable first, Runnable second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (Runnable task : List.of(first, second)) {
            Callable<Boolean> attempt = () -> {
                start.await();
                try {
                    task.run();
                    return true;
                } catch (DataAccessException expected) {
                    return false;
                }
            };
            results.add(pool.submit(attempt));
        }
        start.countDown();
        List<Boolean> outcome = new ArrayList<>();
        for (Future<Boolean> result : results) {
            outcome.add(result.get());
        }
        return outcome;
    }

    @Test
    void cancelAndConfirmAtTheSameTimeLeaveAConsistentBooking() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            int id = createBooking(1, FIRST_TUESDAY.plusWeeks(round));

            race(
                    () -> jdbc.update("CALL sp_cancel_booking(?, ?)", id, "Race test"),
                    () -> jdbc.update("CALL sp_confirm_booking(?)", id));

            String status = jdbc.queryForObject("SELECT status FROM bookings WHERE booking_id = ?", String.class, id);
            int cancellations =
                    jdbc.queryForObject("SELECT COUNT(*) FROM cancellations WHERE booking_id = ?", Integer.class, id);

            // Confirm-then-cancel and cancel-then-confirm both end cancelled;
            // what must never happen is "confirmed" with a cancellation record.
            assertThat(status).isEqualTo("cancelled");
            assertThat(cancellations).isEqualTo(1);
        }
    }

    @Test
    void twoCancelsAtTheSameTimeCreateOneCancellation() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            int id = createBooking(1, FIRST_TUESDAY.plusWeeks(round));

            List<Boolean> outcome = race(
                    () -> jdbc.update("CALL sp_cancel_booking(?, ?)", id, "First"),
                    () -> jdbc.update("CALL sp_cancel_booking(?, ?)", id, "Second"));

            assertThat(outcome).containsExactlyInAnyOrder(true, false);
        }
    }

    @Test
    void twoCustomersBookingTheSameSlotOnlyOneWins() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            LocalDate date = FIRST_TUESDAY.plusWeeks(round);

            List<Boolean> outcome = race(() -> createBooking(1, date), () -> createBooking(3, date));

            assertThat(outcome).containsExactlyInAnyOrder(true, false);
        }
    }
}
