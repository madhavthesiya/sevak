package com.sevak.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sevak.api.booking.BookingRepository;
import com.sevak.api.customer.CustomerRepository;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Row-level security, tested without the Java ownership checks.
 *
 * <p>Most tests log in as sevak_app, the API's real role, and run plain SQL, as a buggy query or a
 * forgotten {@code WHERE customer_id = ...} would. Each one runs in a transaction that is rolled back.
 *
 * <p>In the demo data: customer 1 (user 1) has bookings 1 and 2; customer 2 (user 2) has bookings 3 and 9;
 * provider 1 (user 8) has bookings 1, 4 and 10; user 14 is an admin.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RowLevelSecurityTest extends IntegrationTest {

    private static final String DENIED = "42501"; // insufficient_privilege, also raised for a policy violation

    /** Tables deliberately left without row-level security. Every other table must have it, forced. */
    private static final Set<String> NO_RLS = Set.of(
            "users", // names are public on profiles and reviews; login reads it before anyone is known
            "customers",
            "admins",
            "admin_permissions",
            "locations", // shared map points; who lives there is in customer_addresses, which has RLS
            "coupons",
            "cities",
            "areas",
            "categories",
            "services",
            "service_variants",
            "booking_status_transitions",
            "provider_service_areas",
            "flyway_schema_history");

    @Value("${spring.flyway.placeholders.app_password}")
    private String appPassword;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private BookingRepository bookings;

    private Connection app;

    @BeforeEach
    void logInAsTheApiRole() throws SQLException {
        app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "sevak_app", appPassword);
        app.setAutoCommit(false);
        run("SET search_path = sevak");
    }

    @AfterEach
    void rollBack() throws SQLException {
        app.rollback();
        app.close();
    }

    // ── Customers ───────────────────────────────────────────

    @Test
    void aCustomerSeesOnlyTheirOwnBookings() throws SQLException {
        actAs(1);
        assertThat(ids("SELECT booking_id FROM bookings ORDER BY booking_id")).containsExactly(1, 2);
    }

    @Test
    void aCustomerCannotReadAnythingUnderAnotherCustomersBooking() throws SQLException {
        actAs(1);
        assertThat(count("SELECT COUNT(*) FROM bookings WHERE booking_id = 3")).isZero();
        assertThat(count("SELECT COUNT(*) FROM booking_items WHERE booking_id = 3"))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM payments WHERE booking_id = 3")).isZero();
        assertThat(count("SELECT COUNT(*) FROM booking_status_log WHERE booking_id = 3"))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM coupon_redemptions WHERE booking_id = 3"))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM cancellations WHERE booking_id = 8"))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM refunds")).isZero(); // the only refund is customer 3's

        actAs(2); // the owner of booking 3 does see it all
        assertThat(count("SELECT COUNT(*) FROM booking_items WHERE booking_id = 3"))
                .isPositive();
        assertThat(count("SELECT COUNT(*) FROM payments WHERE booking_id = 3")).isPositive();
    }

    @Test
    void aCustomerCannotChangeAnotherCustomersBooking() throws SQLException {
        actAs(1);
        assertThat(update("UPDATE bookings SET status = 'cancelled' WHERE booking_id = 3"))
                .isZero();
        assertThat(update("DELETE FROM coupon_redemptions WHERE booking_id = 3"))
                .isZero();
    }

    @Test
    void aCustomerCannotBookPayOrReviewInSomeoneElsesName() throws SQLException {
        actAs(1);
        assertDenied("""
                INSERT INTO bookings (customer_id, provider_id, location_id, scheduled_start, scheduled_end,
                                      status, subtotal, discount, total_amount)
                VALUES (2, 1, 3, now() + interval '30 days', now() + interval '30 days 1 hour', 'pending', 0, 0, 0)
                """);
        assertDenied("INSERT INTO booking_items (booking_id, item_no, service_id, quantity, unit_price)"
                + " VALUES (3, 9, 1, 1, 1)");
        assertDenied(
                "INSERT INTO payments (booking_id, payment_method, status, amount) VALUES (3, 'UPI', 'success', 1)");
        // The review trigger runs before the policy, cannot see booking 9 either, and refuses first (SV409).
        assertRejected("INSERT INTO provider_reviews (booking_id, rating) VALUES (9, 1)");
    }

    @Test
    void aCustomersAddressesAreTheirsAlone() throws SQLException {
        actAs(1);
        assertThat(ids("SELECT location_id FROM customer_addresses ORDER BY location_id"))
                .containsExactly(1, 2);
        assertThat(update("UPDATE customer_addresses SET label = 'Mine' WHERE customer_id = 2"))
                .isZero();
        assertDenied("INSERT INTO customer_addresses (customer_id, location_id, label) VALUES (2, 1, 'Mine')");
    }

    @Test
    void complaintsAreSeenByWhoRaisedThemAndHandledOnlyByAdmins() throws SQLException {
        actAs(2);
        assertThat(ids("SELECT booking_id FROM complaints")).containsExactly(9);
        assertThat(update("UPDATE complaints SET status = 'closed'")).isZero();
        assertDenied("INSERT INTO complaints (booking_id, subject, raised_by) VALUES (9, 'Not me', 3)");

        actAs(14);
        assertThat(count("SELECT COUNT(*) FROM complaints")).isEqualTo(3);
        assertThat(update("UPDATE complaints SET status = 'closed' WHERE booking_id = 9"))
                .isOne();
    }

    @Test
    void idempotencyKeysAreSeenOnlyByTheirUser() throws SQLException {
        actAs(1);
        run("INSERT INTO idempotency_keys (user_id, idempotency_key, request_hash) VALUES (1, 'k1', 'h')");
        assertDenied("INSERT INTO idempotency_keys (user_id, idempotency_key, request_hash) VALUES (2, 'k2', 'h')");

        actAs(2);
        assertThat(count("SELECT COUNT(*) FROM idempotency_keys")).isZero();
    }

    // ── Providers ───────────────────────────────────────────

    @Test
    void aProviderSeesOnlyTheirOwnJobs() throws SQLException {
        actAs(8);
        assertThat(ids("SELECT booking_id FROM bookings ORDER BY booking_id")).containsExactly(1, 4, 10);
        assertThat(update("UPDATE bookings SET status = 'confirmed' WHERE booking_id = 6"))
                .isZero(); // provider 5's request
    }

    @Test
    void aProviderCannotChangeAnotherProvidersServicesOrHours() throws SQLException {
        actAs(8);
        assertThat(update("UPDATE provider_services SET price = 1 WHERE provider_id = 2"))
                .isZero();
        assertThat(update("DELETE FROM provider_availability WHERE provider_id = 2"))
                .isZero();
        assertDenied("INSERT INTO provider_services (provider_id, service_id, price) VALUES (2, 1, 1)");
        assertThat(update("UPDATE provider_services SET price = price WHERE provider_id = 1"))
                .isPositive(); // their own
    }

    @Test
    void onlyAdminsVerifyProvidersAndNobodySetsARatingByHand() throws SQLException {
        actAs(8);
        assertThat(update("UPDATE service_providers SET verification_status = 'verified' WHERE provider_id = 6"))
                .isZero();
        assertDenied("UPDATE service_providers SET avg_rating = 5 WHERE provider_id = 1");

        actAs(14);
        assertThat(update("UPDATE service_providers SET verification_status = 'verified' WHERE provider_id = 6"))
                .isOne();
    }

    @Test
    void aProvidersDocumentsAreSeenOnlyByThemAndAdmins() throws SQLException {
        actAs(9);
        assertThat(count("SELECT COUNT(*) FROM provider_documents")).isOne();
        actAs(14);
        assertThat(count("SELECT COUNT(*) FROM provider_documents")).isEqualTo(6);
    }

    // ── Only the values the app writes (found in the Phase 8 review) ──

    @Test
    void aCustomerCannotCreateARefundThatIsAlreadyProcessed() throws SQLException {
        actAs(1);
        assertDenied("""
                INSERT INTO refunds (payment_id, amount, status)
                SELECT payment_id, amount, 'processed' FROM payments WHERE booking_id = 1 AND status = 'success'
                """);
        // What sp_cancel_booking does still works, and the refund starts pending.
        run("INSERT INTO refunds (payment_id, amount) SELECT payment_id, amount FROM payments"
                + " WHERE booking_id = 1 AND status = 'success'");
        assertThat(count("SELECT COUNT(*) FROM refunds r JOIN payments p USING (payment_id)"
                        + " WHERE p.booking_id = 1 AND r.status = 'pending'"))
                .isOne();
    }

    @Test
    void aNewProviderCannotRegisterAlreadyVerifiedOrRated() throws SQLException {
        int userId = (int) count("""
                INSERT INTO users (email, password_hash, role, full_name)
                VALUES ('new.provider@example.com', 'x', 'provider', 'New Provider')
                RETURNING user_id
                """);
        actAs(userId);
        assertDenied("INSERT INTO service_providers (user_id, city_id, verification_status) VALUES (" + userId
                + ", 1, 'verified')");
        assertDenied("INSERT INTO service_providers (user_id, city_id, avg_rating, review_count) VALUES (" + userId
                + ", 1, 5.00, 500)");
        run("INSERT INTO service_providers (user_id, city_id, bio, experience_years) VALUES (" + userId
                + ", 1, 'Hello', 2)"); // what registration does
    }

    @Test
    void aCustomerCannotCreateABookingThatIsAlreadyCompleted() throws SQLException {
        actAs(1);
        assertDenied("""
                INSERT INTO bookings (customer_id, provider_id, location_id, scheduled_start, scheduled_end,
                                      status, subtotal, discount, total_amount)
                VALUES (1, 2, 1, now() + interval '40 days', now() + interval '40 days 1 hour', 'completed', 0, 0, 0)
                """);
    }

    @Test
    void aCustomerCanOnlyCancelTheirOwnBooking() throws SQLException {
        actAs(7); // booking 10 is customer 7's pending request
        assertDenied("UPDATE bookings SET status = 'confirmed' WHERE booking_id = 10");
        assertThat(update("UPDATE bookings SET status = 'cancelled' WHERE booking_id = 10"))
                .isOne();
    }

    @Test
    void aProviderCannotCreateAVerifiedDocument() throws SQLException {
        actAs(8);
        assertDenied("INSERT INTO provider_documents (provider_id, document_type, file_url, status)"
                + " VALUES (1, 'Licence', '/docs/x.pdf', 'verified')");
    }

    @Test
    void aCustomerCannotChangeASharedMapPoint() throws SQLException {
        actAs(1);
        assertDenied("UPDATE locations SET street = 'Somewhere else' WHERE location_id = 3");
        // The address upsert's DO UPDATE touches only latitude, which is still allowed.
        assertThat(update("UPDATE locations SET latitude = latitude WHERE location_id = 1"))
                .isOne();
    }

    // ── Nobody logged in, and admins ────────────────────────

    @Test
    void withNoUserNoPrivateRowIsVisible() throws SQLException {
        for (String table : List.of(
                "bookings",
                "booking_items",
                "booking_status_log",
                "payments",
                "refunds",
                "cancellations",
                "coupon_redemptions",
                "complaints",
                "customer_addresses",
                "provider_documents")) {
            assertThat(count("SELECT COUNT(*) FROM " + table)).as(table).isZero();
        }
    }

    @Test
    void publicScreensStillWorkWithNoUser() throws SQLException {
        assertThat(count("SELECT COUNT(*) FROM service_providers")).isEqualTo(6);
        assertThat(count("SELECT COUNT(*) FROM provider_reviews")).isEqualTo(5);
        assertThat(count("SELECT COUNT(*) FROM v_public_reviews WHERE provider_id = 1"))
                .isOne();
        // Provider 1's two open bookings (4 and 10) still block their times on the free-slots screen.
        assertThat(count("""
                        SELECT COUNT(*) FROM fn_provider_busy_times(1, now() - interval '1 year', now() + interval '1 year')
                        """)).isEqualTo(2);
    }

    @Test
    void anAdminSeesEveryBooking() throws SQLException {
        actAs(14);
        assertThat(count("SELECT COUNT(*) FROM bookings")).isEqualTo(10);
        assertThat(count("SELECT COUNT(*) FROM payments")).isEqualTo(9);
    }

    @Test
    void onlyAdminsReadTheLiveLeaderboardAndNobodyReadsItsViewDirectly() throws SQLException {
        actAs(14);
        assertThat(count("SELECT COUNT(*) FROM fn_live_leaderboard()")).isEqualTo(5); // the 5 active providers

        actAs(1);
        assertDenied("SELECT COUNT(*) FROM fn_live_leaderboard()");
        assertDenied("SELECT COUNT(*) FROM v_provider_leaderboard");
    }

    // ── What the role can't do at all ──────────────────────

    @Test
    void theApiRoleCannotReadPasswordHashes() throws SQLException {
        assertDenied("SELECT password_hash FROM users");
        assertDenied("SELECT * FROM users");
        assertThat(count("SELECT COUNT(*) FROM fn_login_credentials('aarav.sharma@gmail.com')"))
                .isOne();
    }

    @Test
    void theApiRoleCannotEmptyTablesForgeTheAuditLogOrChangePayments() throws SQLException {
        actAs(1);
        assertDenied("TRUNCATE bookings");
        assertDenied("INSERT INTO booking_status_log (booking_id, new_status) VALUES (1, 'completed')");
        assertDenied("UPDATE payments SET status = 'refunded' WHERE booking_id = 1");
        assertDenied("UPDATE bookings SET total_amount = 0 WHERE booking_id = 1"); // only status may change
        assertDenied("DELETE FROM bookings WHERE booking_id = 1");
    }

    // ── Through the app's own code, with the Java check skipped ──

    @Test
    void repositoryMethodsThatTrustTheirCallerReturnNothingForSomeoneElsesBooking() {
        // findBookingLines and findTimeline filter only by booking ID: the service checks ownership first.
        // Called directly, as customer 1, for customer 2's booking 3, the policies still hide it.
        assertThat(asUser(1, () -> customers.findBookingLines(3))).isEmpty();
        assertThat(asUser(1, () -> customers.findTimeline(3))).isEmpty();
        assertThat(asUser(1, () -> bookings.findParties(3))).isEmpty();

        assertThat(asUser(2, () -> customers.findBookingLines(3))).isNotEmpty();
        assertThat(asUser(2, () -> bookings.findParties(3))).isPresent();
    }

    // ── Guards for the future ───────────────────────────────

    @Test
    void everyTableHasForcedRowLevelSecurityOrIsDeliberatelyPublic() {
        List<String> unprotected = jdbc.queryForList("""
                        SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                        WHERE n.nspname = 'sevak' AND c.relkind = 'r'
                          AND NOT (c.relrowsecurity AND c.relforcerowsecurity)
                        ORDER BY 1
                        """, String.class);
        assertThat(unprotected)
                .as("a new table must get policies, or be added to NO_RLS with a reason")
                .allMatch(NO_RLS::contains);
    }

    @Test
    void everySecurityDefinerFunctionHasAFixedSearchPathAndIsNotPublic() {
        List<String> unsafe = jdbc.queryForList("""
                        SELECT p.proname FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
                        WHERE n.nspname = 'sevak' AND p.prosecdef
                          AND (NOT EXISTS (SELECT 1 FROM unnest(p.proconfig) c WHERE c LIKE 'search_path=%')
                               OR has_function_privilege('public', p.oid, 'EXECUTE'))
                        """, String.class);
        assertThat(unsafe).isEmpty();
    }

    @Test
    void forceAppliesThePoliciesToAnOrdinaryOwner() throws SQLException {
        // Superusers skip policies even with FORCE, so give bookings an ordinary owner for this test.
        try (Connection owner = dataSource.getConnection()) {
            owner.setAutoCommit(false);
            try (Statement sql = owner.createStatement()) {
                sql.execute("CREATE ROLE rls_test_owner NOLOGIN");
                sql.execute("GRANT USAGE ON SCHEMA sevak TO rls_test_owner");
                sql.execute("GRANT SELECT ON sevak.customers, sevak.service_providers, sevak.admins TO rls_test_owner");
                sql.execute("ALTER TABLE sevak.bookings OWNER TO rls_test_owner");
                sql.execute("SET LOCAL ROLE rls_test_owner");
                assertThat(single(sql, "SELECT COUNT(*) FROM sevak.bookings")).isZero();

                sql.execute("RESET ROLE");
                sql.execute("ALTER TABLE sevak.bookings NO FORCE ROW LEVEL SECURITY");
                sql.execute("SET LOCAL ROLE rls_test_owner");
                assertThat(single(sql, "SELECT COUNT(*) FROM sevak.bookings")).isEqualTo(10);
            } finally {
                owner.rollback();
            }
        }
    }

    // ── Helpers ─────────────────────────────────────────────

    private void actAs(int userId) throws SQLException {
        run("SELECT set_config('sevak.user_id', '" + userId + "', true)");
    }

    private void run(String sql) throws SQLException {
        try (Statement statement = app.createStatement()) {
            statement.execute(sql);
        }
    }

    private int update(String sql) throws SQLException {
        try (Statement statement = app.createStatement()) {
            return statement.executeUpdate(sql);
        }
    }

    private long count(String sql) throws SQLException {
        try (Statement statement = app.createStatement()) {
            return single(statement, sql);
        }
    }

    private static long single(Statement statement, String sql) throws SQLException {
        try (ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private List<Integer> ids(String sql) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        try (Statement statement = app.createStatement();
                ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                ids.add(rs.getInt(1));
            }
        }
        return ids;
    }

    /** Fails with "permission denied" or "violates row-level security policy"; the transaction carries on. */
    private void assertDenied(String sql) throws SQLException {
        Savepoint before = app.setSavepoint();
        assertThatThrownBy(() -> run(sql))
                .isInstanceOfSatisfying(
                        SQLException.class,
                        ex -> assertThat(ex.getSQLState()).as(ex.getMessage()).isEqualTo(DENIED));
        app.rollback(before);
    }

    /** Refused by the database for any reason; the transaction carries on. */
    private void assertRejected(String sql) throws SQLException {
        Savepoint before = app.setSavepoint();
        assertThatThrownBy(() -> run(sql)).isInstanceOf(SQLException.class);
        app.rollback(before);
    }
}
