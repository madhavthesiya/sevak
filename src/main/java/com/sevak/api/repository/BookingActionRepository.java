package com.sevak.api.repository;

import com.sevak.api.exception.ConflictException;
import com.sevak.api.exception.NotFoundException;
import com.sevak.api.exception.UnprocessableException;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
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
        } catch (DataAccessException ex) {
            throw translate(ex, bookingId);
        }
        return Map.of(
                "success", true,
                "message", "Booking " + bookingId + " cancelled successfully",
                "booking_id", bookingId
        );
    }

    // ── Confirm Booking (Calls Stored Procedure) ────────────
    public Map<String, Object> confirmBooking(int bookingId) {
        try {
            jdbc.update("CALL sp_confirm_booking(?)", bookingId);
        } catch (DataAccessException ex) {
            throw translate(ex, bookingId);
        }
        return Map.of(
                "success", true,
                "message", "Booking " + bookingId + " confirmed successfully",
                "booking_id", bookingId
        );
    }

    /**
     * Maps the custom SQLSTATE codes raised by our stored procedures to API exceptions.
     * SV404 = booking not found, SV409 = wrong booking status, SV422 = business rule broken.
     * Any other database error is re-thrown and becomes a generic 500 (details only in the logs).
     */
    private static RuntimeException translate(DataAccessException ex, int bookingId) {
        Throwable cause = ex.getMostSpecificCause();
        String state = cause instanceof SQLException sql ? sql.getSQLState() : null;
        if (state == null) {
            return ex;
        }
        return switch (state) {
            case "SV404" -> new NotFoundException("Booking " + bookingId + " not found");
            case "SV409" -> new ConflictException(procedureMessage(cause));
            case "SV422" -> new UnprocessableException(procedureMessage(cause));
            default -> ex;
        };
    }

    /**
     * Returns only the MESSAGE text written in our RAISE EXCEPTION statement.
     * PSQLException.getMessage() would also include "ERROR:" and the PL/pgSQL function and line
     * ("Where: ..."), which we don't want to show to API clients.
     */
    private static String procedureMessage(Throwable cause) {
        if (cause instanceof PSQLException psql) {
            ServerErrorMessage server = psql.getServerErrorMessage();
            if (server != null && server.getMessage() != null) {
                return server.getMessage();
            }
        }
        return "The booking could not be updated";
    }
}
