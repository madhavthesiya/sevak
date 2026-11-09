package com.sevak.api;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasKey;
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
                "/api/general/services",
                "/api/general/services/search?q=clean",
                "/api/general/providers/top",
                "/api/general/providers/available?day=Monday",
                "/api/customers/1/bookings",
                "/api/customers/1/bookings/1/items",
                "/api/customers/1/payments",
                "/api/customers/1/addresses",
                "/api/providers/1/completed",
                "/api/providers/1/earnings",
                "/api/providers/1/reviews",
                "/api/providers/1/rating",
                "/api/admin/revenue",
                "/api/admin/leaderboard",
                "/api/admin/leaderboard?fast=true",
                "/api/admin/complaints",
                "/api/admin/city-revenue"
            })
    void listEndpointsReturnData(String url) throws Exception {
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(jsonPath("$.length()", greaterThan(0)));
    }

    @Test
    void pendingJobsReturnTheProvidersPendingBooking() throws Exception {
        mvc.perform(get("/api/providers/1/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].booking_id").value(10));
    }

    @Test
    void spendingSummaryIsASingleObject() throws Exception {
        mvc.perform(get("/api/customers/1/spending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasKey("paid")))
                .andExpect(jsonPath("$", hasKey("upcoming")))
                .andExpect(jsonPath("$", hasKey("refunded")));
    }

    @Test
    void healthIsUpWhenTheDatabaseIsReachable() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database").value("connected"));
    }

    @Test
    void openApiDocsAreServed() throws Exception {
        mvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.apiKey.name").value("X-Api-Key"));
    }
}
