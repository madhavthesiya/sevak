package com.sevak.api.common;

import java.sql.SQLException;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataAccessException;

/** Reads the details PostgreSQL attaches to an error. */
public final class PgErrors {

    public static final String UNIQUE_VIOLATION = "23505";
    public static final String FOREIGN_KEY_VIOLATION = "23503";
    public static final String EXCLUSION_VIOLATION = "23P01";

    private PgErrors() {}

    public static String sqlState(DataAccessException ex) {
        return ex.getMostSpecificCause() instanceof SQLException sql ? sql.getSQLState() : null;
    }

    /**
     * Returns only the MESSAGE text written in a RAISE EXCEPTION statement. PSQLException.getMessage()
     * would also include "ERROR:" and the PL/pgSQL function and line, which API clients shouldn't see.
     */
    public static String procedureMessage(DataAccessException ex) {
        if (ex.getMostSpecificCause() instanceof PSQLException psql) {
            ServerErrorMessage server = psql.getServerErrorMessage();
            if (server != null && server.getMessage() != null) {
                return server.getMessage();
            }
        }
        return "The request could not be completed";
    }
}
