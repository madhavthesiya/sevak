package com.sevak.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Starts the app pointing at a database that doesn't exist. */
@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:postgresql://localhost:1/none",
            "spring.flyway.enabled=false",
            "spring.datasource.hikari.initialization-fail-timeout=-1",
            "spring.datasource.hikari.connection-timeout=250"
        })
@AutoConfigureMockMvc
class HealthWhenDatabaseDownTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void readinessReports503() throws Exception {
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void livenessStaysUpSoTheContainerIsNotRestartedForADatabaseOutage() throws Exception {
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    }
}
