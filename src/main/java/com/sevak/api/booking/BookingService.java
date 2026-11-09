package com.sevak.api.booking;

import com.sevak.api.booking.dto.BookingActionResult;
import com.sevak.api.common.ConflictException;
import com.sevak.api.common.NotFoundException;
import com.sevak.api.common.UnprocessableException;
import java.sql.SQLException;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

    private final BookingRepository repo;

    public BookingService(BookingRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public BookingActionResult cancel(int bookingId, String reason) {
        try {
            repo.cancel(bookingId, reason);
        } catch (DataAccessException ex) {
            throw translate(ex, bookingId);
        }
        return new BookingActionResult(bookingId, "cancelled", "Booking " + bookingId + " cancelled");
    }

    @Transactional
    public BookingActionResult accept(int bookingId) {
        try {
            repo.confirm(bookingId);
        } catch (DataAccessException ex) {
            throw translate(ex, bookingId);
        }
        return new BookingActionResult(bookingId, "confirmed", "Booking " + bookingId + " confirmed");
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
     * ("Where: ..."), which API clients shouldn't see.
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
