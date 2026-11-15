package com.sevak.api.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Refuses to start the API if row-level security wouldn't apply to its requests.
 *
 * <p>Superusers, roles with BYPASSRLS and (without FORCE) table owners skip every policy without any
 * error, so a wrong login in production would silently turn the protection off. This makes it loud.
 */
@Component
class AppRoleCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AppRoleCheck.class);

    record RoleFacts(
            String role, boolean superuser, boolean bypassRls, boolean ownsTables, boolean rlsOn, boolean rlsForced) {}

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;

    AppRoleCheck(JdbcClient jdbc, PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactions);
        this.tx.setReadOnly(true);
    }

    @Override
    public void run(ApplicationArguments args) {
        RoleFacts facts;
        try {
            // Inside a transaction, so this sees the role that requests really run as.
            facts = tx.execute(status -> jdbc.sql("""
                            SELECT current_user AS role, r.rolsuper AS superuser, r.rolbypassrls AS bypass_rls,
                                   pg_has_role(current_user, c.relowner, 'USAGE') AS owns_tables,
                                   c.relrowsecurity AS rls_on, c.relforcerowsecurity AS rls_forced
                            FROM pg_roles r, pg_class c
                            WHERE r.rolname = current_user AND c.oid = 'sevak.bookings'::regclass
                            """).query(RoleFacts.class).single());
        } catch (CannotCreateTransactionException ex) {
            // The readiness check reports an unreachable database; the policies are checked at the next start.
            log.warn("Could not check the API's database role: the database is unreachable");
            return;
        }
        if (facts.superuser() || facts.bypassRls() || facts.ownsTables()) {
            throw new IllegalStateException("The API's database role '" + facts.role()
                    + "' would skip row-level security (superuser, BYPASSRLS or table owner). Connect as sevak_app.");
        }
        if (!facts.rlsOn() || !facts.rlsForced()) {
            throw new IllegalStateException("Row-level security is not enabled and forced on sevak.bookings.");
        }
        log.info("Requests run as '{}', under row-level security", facts.role());
    }
}
