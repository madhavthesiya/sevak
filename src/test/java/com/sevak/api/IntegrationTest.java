package com.sevak.api;

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
@SpringBootTest(properties = "sevak.admin-key=" + IntegrationTest.API_KEY)
@AutoConfigureMockMvc
@Transactional
public abstract class IntegrationTest {

    protected static final String API_KEY = "test-key";
    protected static final String API_KEY_HEADER = "X-Api-Key";

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;
}
