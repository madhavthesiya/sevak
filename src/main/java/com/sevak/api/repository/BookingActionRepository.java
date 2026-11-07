package com.sevak.api.repository;

import com.sevak.api.exception.ConflictException;
import com.sevak.api.exception.NotFoundException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.util.Map;

@Repository
public class BookingActionRepository {

    private final JdbcTemplate jdbc;

    public BookingActionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── Cancel Booking (Calls Stored Procedure) ─────────────
    public Map<String, Object> cancelBooking(int bookingId, String reason) {
        try {
            jdbc.update("CALL sp_cancel_booking(?, ?)", bookingId, reason);
            return Map.of(
                    "success", true,
                    "message", "Booking " + bookingId + " cancelled successfully",
                    "booking_id", bookingId
            );
        } catch (DataAccessException ex) {
            String state = ex.getMostSpecificCause() instanceof SQLException sql ? sql.getSQLState() : null;
            switch (String.valueOf(state)) {
                case "SV404" -> throw new NotFoundException("Booking " + bookingId + " not found");
                case "SV409" -> throw new ConflictException("Booking " + bookingId + " cannot be cancelled in its current state");
                default      -> throw ex;
            }
        }
    }

    // ── Confirm Booking (Calls Stored Procedure) ────────────
    public Map<String, Object> confirmBooking(int bookingId) {
        try {
            jdbc.update("CALL sp_confirm_booking(?)", bookingId);
            return Map.of(
                    "success", true,
                    "message", "Booking " + bookingId + " confirmed successfully",
                    "booking_id", bookingId
            );
        } catch (DataAccessException ex) {
            String state = ex.getMostSpecificCause() instanceof SQLException sql ? sql.getSQLState() : null;
            switch (String.valueOf(state)) {
                case "SV404" -> throw new NotFoundException("Booking " + bookingId + " not found");
                case "SV409" -> throw new ConflictException("Booking " + bookingId + " cannot be confirmed in its current state");
                default      -> throw ex;
            }
        }
    }
}
