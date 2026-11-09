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
                "/api/v1/providers/1/reviews"
            })
    void publicPagedEndpointsReturnData(String url) throws Exception {
        mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", greaterThan(0)))
                .andExpect(jsonPath("$.total", greaterThan(0)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/me/bookings", "/api/v1/me/payments"})
    void customerPagedEndpointsReturnData(String url) throws Exception {
        mvc.perform(get(url).header(AUTH, customer(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", greaterThan(0)));
    }

    @Test
    void addressesReturnData() throws Exception {
        mvc.perform(get("/api/v1/me/addresses").header(AUTH, customer(1)))
                .andExpect(jsonPath("$.length()", greaterThan(0)));
    }

    @Test
    void providerWorkReturnsData() throws Exception {
        mvc.perform(get("/api/v1/provider/jobs?status=completed").header(AUTH, provider(1)))
                .andExpect(jsonPath("$.data.length()", greaterThan(0)));
        mvc.perform(get("/api/v1/provider/earnings").header(AUTH, provider(1)))
                .andExpect(jsonPath("$.length()", greaterThan(0)));
    }

    @Test
    void topProvidersArePublic() throws Exception {
        mvc.perform(get("/api/v1/providers/top"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThan(0)));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/api/v1/admin/revenue",
                "/api/v1/admin/leaderboard",
                "/api/v1/admin/leaderboard?fast=true",
                "/api/v1/admin/complaints",
                "/api/v1/admin/city-revenue"
            })
    void adminEndpointsReturnData(String url) throws Exception {
        mvc.perform(get(url).header(AUTH, admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThan(0)));
    }

    @Test
    void pendingJobsAreTheDefault() throws Exception {
        mvc.perform(get("/api/v1/provider/jobs").header(AUTH, provider(1)))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].bookingId").value(10));
    }

    @Test
    void openApiDocsAreServed() throws Exception {
        mvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme")
                        .value("bearer"))
                .andExpect(jsonPath("$.paths['/api/v1/bookings'].post.responses['401']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/services'].get.responses['400']")
                        .exists());
    }
}
