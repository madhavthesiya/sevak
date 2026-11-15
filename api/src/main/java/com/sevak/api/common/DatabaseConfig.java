package com.sevak.api.common;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.flyway.autoconfigure.FlywayConnectionDetails;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Two ways into the database.
 *
 * <ul>
 *   <li>The main pool (Spring Boot's auto-configured one) serves requests as {@code sevak_app}, under
 *       row-level security.
 *   <li>A small owner pool, with Flyway's login, for the maintenance work only the owner may do:
 *       refreshing the materialized views and resetting the demo data. It runs fixed statements only;
 *       nothing a user sends reaches it.
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class DatabaseConfig {

    /** Qualifier for the owner pool. */
    public static final String OWNER = "owner";

    @Bean
    UserContextTransactionManager transactionManager(
            DataSource dataSource, @Value("${sevak.db.app-role}") String role) {
        return new UserContextTransactionManager(dataSource, role);
    }

    /**
     * Not a default candidate: anything that asks for "a DataSource" still gets the main pool, and Spring
     * Boot still auto-configures it. Uses Flyway's URL and login, falling back to the main pool's.
     */
    @Bean(defaultCandidate = false)
    @Qualifier(OWNER)
    @ConfigurationProperties("sevak.owner-db.hikari")
    HikariDataSource ownerDataSource(JdbcConnectionDetails app, ObjectProvider<FlywayConnectionDetails> flyway) {
        FlywayConnectionDetails owner = flyway.getIfAvailable();
        boolean ownLogin = owner != null && owner.getUsername() != null;
        HikariDataSource pool = new HikariDataSource();
        pool.setPoolName("owner");
        pool.setJdbcUrl(owner != null && owner.getJdbcUrl() != null ? owner.getJdbcUrl() : app.getJdbcUrl());
        pool.setUsername(ownLogin ? owner.getUsername() : app.getUsername());
        pool.setPassword(ownLogin ? owner.getPassword() : app.getPassword());
        return pool;
    }
}
