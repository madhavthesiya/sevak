package com.sevak.api;

import com.sevak.api.auth.TokenService;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base class for tests that need the real database.
 *
 * <p>One PostgreSQL container is started once and shared by all test classes; Flyway builds the
 * schema and loads the demo data into it. Each test runs in a transaction that is rolled back
 * afterwards, so tests can change data without affecting each other.
 *
 * <p>The app logs in as the container's superuser here, and every one of its transactions switches to
 * sevak_app (see UserContextTransactionManager), so requests run under row-level security exactly as
 * in production. RowLevelSecurityTest also logs in as sevak_app itself.
 */
@SpringBootTest(
        properties = {
            "sevak.rate-limit.strict-per-minute=1000000",
            "sevak.rate-limit.general-per-minute=1000000",
            // Logging in as the owner keeps each test rollback-able; see AppRoleCheck.
            "sevak.db.allow-privileged-login=true"
        })
@AutoConfigureMockMvc
@Transactional
public abstract class IntegrationTest {

    protected static final String AUTH = "Authorization";

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    /** Flyway and the owner pool use the container's superuser, the owner of everything. */
    @DynamicPropertySource
    static void ownerLogin(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @Autowired
    protected MockMvc mvc;

    /**
     * The test's own SQL, run as the owner: setting up data and checking results must see every row.
     * It shares the test's transaction with the requests, so it first undoes the role a request switched to.
     */
    protected JdbcTemplate jdbc;

    @Autowired
    private TokenService tokens;

    @Autowired
    void ownerJdbc(DataSource dataSource) {
        jdbc = new JdbcTemplate(dataSource) {
            @Override
            protected void applyStatementSettings(Statement statement) throws SQLException {
                try (Statement reset = statement.getConnection().createStatement()) {
                    reset.execute("RESET ROLE");
                }
                super.applyStatementSettings(statement);
            }
        };
    }

    // In the demo data: customers are users 1-7, providers are users 8-13, admins are users 14-15.

    protected static final int ADMIN_USER = 14;

    @Autowired
    private PlatformTransactionManager transactions;

    /**
     * Runs app code as the given user, the way a request does: in a transaction (joining the test's, if
     * there is one), as sevak_app, under row-level security.
     */
    protected <T> T asUser(int userId, Supplier<T> work) {
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject(String.valueOf(userId))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        try {
            return new TransactionTemplate(transactions).execute(status -> work.get());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    protected String customer(int customerId) {
        return "Bearer " + tokens.issue(customerId, "customer", customerId);
    }

    protected String provider(int providerId) {
        return "Bearer " + tokens.issue(providerId + 7, "provider", providerId);
    }

    protected String admin() {
        return "Bearer " + tokens.issue(14, "admin", 1);
    }
}
