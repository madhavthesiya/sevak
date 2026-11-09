package com.sevak.api;

import com.sevak.api.auth.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base class for tests that need the real database.
 *
 * <p>One PostgreSQL container is started once and shared by all test classes; Flyway builds the
 * schema and loads the demo data into it. Each test runs in a transaction that is rolled back
 * afterwards, so tests can change data without affecting each other.
 */
@SpringBootTest(
        properties = {"sevak.rate-limit.strict-per-minute=1000000", "sevak.rate-limit.general-per-minute=1000000"})
@AutoConfigureMockMvc
@Transactional
public abstract class IntegrationTest {

    protected static final String AUTH = "Authorization";

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    private TokenService tokens;

    // In the demo data: customers are users 1-7, providers are users 8-13, admins are users 14-15.

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
