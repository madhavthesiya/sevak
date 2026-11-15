package com.sevak.api.common;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Tells PostgreSQL who is calling, at the start of every transaction, reads included. The row-level
 * security policies (V14) use it to decide which rows the request may see or change.
 *
 * <p>Two settings, both local to the transaction, so a pooled connection never carries them into the
 * next request:
 *
 * <ul>
 *   <li>{@code role}: the API's database role. In production the API already logs in as it, so this
 *       changes nothing; where it logs in as the owner (the tests), requests still run under the policies.
 *   <li>{@code sevak.user_id}: the logged-in user from the verified token, or empty for anonymous
 *       requests and background jobs. Empty means no private rows: a missing user fails closed.
 * </ul>
 */
public class UserContextTransactionManager extends JdbcTransactionManager {

    private static final String APPLY = "SELECT set_config('role', ?, true), set_config('sevak.user_id', ?, true)";

    private final String appRole;

    public UserContextTransactionManager(DataSource dataSource, String appRole) {
        super(dataSource);
        this.appRole = appRole;
    }

    /** A new transaction. A failure here releases the connection and fails the transaction cleanly. */
    @Override
    protected void prepareTransactionalConnection(Connection con, TransactionDefinition definition)
            throws SQLException {
        super.prepareTransactionalConnection(con, definition);
        apply(con);
    }

    /**
     * A method joining a transaction that is already open gets no begin callback, so the user is applied
     * here too. That happens when one service calls another, and in tests, where every request joins the
     * test's rolled-back transaction.
     */
    @Override
    protected void prepareSynchronization(DefaultTransactionStatus status, TransactionDefinition definition) {
        super.prepareSynchronization(status, definition);
        if (!status.hasTransaction() || status.isNewTransaction()) {
            return;
        }
        if (TransactionSynchronizationManager.getResource(obtainDataSource()) instanceof ConnectionHolder holder) {
            try {
                apply(holder.getConnection());
            } catch (SQLException ex) {
                throw new CannotCreateTransactionException("Could not tell the database who is calling", ex);
            }
        }
    }

    private void apply(Connection con) throws SQLException {
        try (PreparedStatement statement = con.prepareStatement(APPLY)) {
            statement.setString(1, appRole);
            statement.setString(2, currentUserId());
            statement.execute();
        }
    }

    /** The user ID from the request's verified token, or "" when nobody is logged in. */
    static String currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth instanceof JwtAuthenticationToken token ? token.getToken().getSubject() : "";
    }
}
