package com.sevak.api;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Every read endpoint responds successfully with data from the seeded database. */
class ApiSmokeTest extends IntegrationTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/api/v1/services",
                "/api/v1/providers",
                "/api/v1/providers?service=clean",
                "/api/v1/providers?day=Monday",
                "/api/v1/customers/1/bookings",
                "/api/v1/customers/1/payments",
                "/api/v1/providers/1/reviews",
                "/api/v1/providers/1/jobs?status=completed"
            })
    void pagedEndpointsReturnData(String url) throws Exception {
        mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", greaterThan(0)))
                .andExpect(jsonPath("$.total", greaterThan(0)));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/api/v1/providers/top",
                "/api/v1/customers/1/addresses",
                "/api/v1/providers/1/earnings",
                "/api/v1/admin/revenue",
                "/api/v1/admin/leaderboard",
                "/api/v1/admin/leaderboard?fast=true",
                "/api/v1/admin/complaints",
                "/api/v1/admin/city-revenue"
            })
    void listEndpointsReturnData(String url) throws Exception {
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(jsonPath("$.length()", greaterThan(0)));
    }

    @Test
    void pendingJobsAreTheDefault() throws Exception {
        mvc.perform(get("/api/v1/providers/1/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].bookingId").value(10));
    }

    @Test
    void spendingSummaryIsASingleObject() throws Exception {
        mvc.perform(get("/api/v1/customers/1/spending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paid").exists())
                .andExpect(jsonPath("$.upcoming").exists())
                .andExpect(jsonPath("$.refunded").exists());
    }

    @Test
    void openApiDocsAreServed() throws Exception {
        mvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.apiKey.name").value("X-Api-Key"))
                .andExpect(jsonPath("$.components.schemas.ProviderOffering.properties.providerName")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/services'].get.responses['400']")
                        .exists());
    }
}
