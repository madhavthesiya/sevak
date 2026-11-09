package com.sevak.api.controller;

import io.swagger.v3.oas.annotations.Hidden;
import java.time.Instant;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Health check endpoint — used by DigitalOcean/Docker to verify the app is alive.
 * Also useful for uptime monitoring services.
 */
@RestController
public class HealthController {

    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/health")
    @Hidden // Hide from Swagger — internal use only
    public ResponseEntity<Map<String, Object>> health() {
        boolean dbUp;
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            dbUp = true;
        } catch (DataAccessException e) {
            dbUp = false;
        }

        return ResponseEntity.status(dbUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status", dbUp ? "UP" : "DOWN",
                        "database", dbUp ? "connected" : "disconnected",
                        "timestamp", Instant.now().toString()));
    }
}
