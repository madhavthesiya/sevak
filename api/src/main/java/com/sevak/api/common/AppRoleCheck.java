package com.sevak.api.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Refuses to start the API if row-level security wouldn't protect its requests.
 *
 * <p>Superusers, roles with BYPASSRLS and (without FORCE) table owners skip every policy without any
 * error, so a wrong login in production would silently turn the protection off. This makes it loud.
 *
 * <p>Two roles are checked. The role requests run as must be under the policies. The login itself must
 * be too: requests switch to sevak_app for each transaction, but a privileged login could switch back
 * with RESET ROLE, for example through an injected statement. Only the tests log in as the owner, on
 * purpose, so their data can be rolled back; they set {@code sevak.db.allow-privileged-login}.
 */
@Component
class AppRoleCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AppRoleCheck.class);

    record RoleFacts(
            String role,
            boolean privileged,
            String login,
            boolean loginPrivileged,
            String owner,
            boolean ownerBypassesRls,
            boolean rlsOn,
            boolean rlsForced) {}

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final boolean allowPrivilegedLogin;

    AppRoleCheck(
            JdbcClient jdbc,
            PlatformTransactionManager transactions,
            @Value("${sevak.db.allow-privileged-login:false}") boolean allowPrivilegedLogin) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactions);
        this.tx.setReadOnly(true);
        this.allowPrivilegedLogin = allowPrivilegedLogin;
    }

    @Override
    public void run(ApplicationArguments args) {
        RoleFacts facts;
        try {
            // Inside a transaction, so current_user is the role that requests really run as.
            facts = tx.execute(status -> jdbc.sql("""
                            SELECT current_user AS role,
                                   r.rolsuper OR r.rolbypassrls OR pg_has_role(current_user, c.relowner, 'USAGE')
                                       AS privileged,
                                   session_user AS login,
                                   s.rolsuper OR s.rolbypassrls OR pg_has_role(session_user, c.relowner, 'USAGE')
                                       AS login_privileged,
                                   o.rolname AS owner, o.rolsuper OR o.rolbypassrls AS owner_bypasses_rls,
                                   c.relrowsecurity AS rls_on, c.relforcerowsecurity AS rls_forced
                            FROM pg_class c, pg_roles r, pg_roles s, pg_roles o
                            WHERE c.oid = 'sevak.bookings'::regclass
                              AND r.rolname = current_user AND s.rolname = session_user AND o.oid = c.relowner
                            """).query(RoleFacts.class).single());
        } catch (CannotCreateTransactionException ex) {
            // The readiness check reports it; the roles are checked at the next start. The root cause tells a
            // database that is down apart from one that refused the login, such as a wrong password.
            Throwable cause = NestedExceptionUtils.getMostSpecificCause(ex);
            log.warn("Could not check the API's database role: {}", cause.getMessage());
            return;
        }
        if (facts.privileged()) {
            throw new IllegalStateException("Requests would run as '" + facts.role()
                    + "', which skips row-level security (superuser, BYPASSRLS or table owner).");
        }
        if (facts.loginPrivileged() && !allowPrivilegedLogin) {
            throw new IllegalStateException("The API logs in as '" + facts.login()
                    + "', a superuser, BYPASSRLS or table-owner role that could switch back out of '" + facts.role()
                    + "'. Log in as sevak_app.");
        }
        // Flyway, the jobs and every SECURITY DEFINER function run as the owner. Under FORCE an ordinary
        // owner is filtered too: ratings would stop updating and expired requests would never expire.
        if (!facts.ownerBypassesRls()) {
            throw new IllegalStateException("The tables' owner '" + facts.owner()
                    + "' is neither a superuser nor BYPASSRLS, so the policies would filter the jobs and the"
                    + " SECURITY DEFINER functions too. Give it BYPASSRLS or use an owner that has it.");
        }
        if (!facts.rlsOn() || !facts.rlsForced()) {
            throw new IllegalStateException("Row-level security is not enabled and forced on sevak.bookings.");
        }
        log.info("Requests run as '{}' (login '{}'), under row-level security", facts.role(), facts.login());
    }
}
