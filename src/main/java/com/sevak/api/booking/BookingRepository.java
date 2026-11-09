package com.sevak.api.booking;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class BookingRepository {

    private final JdbcClient jdbc;

    public BookingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void cancel(int bookingId, String reason) {
        jdbc.sql("CALL sp_cancel_booking(:id, :reason)")
                .param("id", bookingId)
                .param("reason", reason)
                .update();
    }

    public void confirm(int bookingId) {
        jdbc.sql("CALL sp_confirm_booking(:id)").param("id", bookingId).update();
    }
}
