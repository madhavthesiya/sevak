package com.sevak.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Bad requests get the right status code, and errors never expose internal details. */
class ApiErrorsTest extends IntegrationTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/api/customers/abc/bookings",
                "/api/general/services?page=0",
                "/api/general/services?limit=0",
                "/api/general/services?limit=101",
                "/api/general/providers/top?top=0",
                "/api/general/providers/top?top=51",
                "/api/general/providers/available?day=Funday",
                "/api/general/services/search"
            })
    void invalidInputReturns400(String url) throws Exception {
        mvc.perform(get(url)).andExpect(status().isBadRequest());
    }

    @Test
    void validationErrorNamesTheParameterNotTheJavaMethod() throws Exception {
        mvc.perform(get("/api/general/services?page=0"))
                .andExpect(jsonPath("$.detail").value("page: must be greater than or equal to 1"));
    }

    @Test
    void unknownUrlReturns404() throws Exception {
        mvc.perform(get("/api/does-not-exist")).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Monday", "monday", "MONDAY", " Monday "})
    void dayIsAcceptedInAnyLetterCase(String day) throws Exception {
        mvc.perform(get("/api/general/providers/available").param("day", day))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].day_of_week").value("Monday"));
    }

    @Test
    void unexpectedDatabaseErrorReturnsGeneric500() throws Exception {
        jdbc.execute("ALTER TABLE categories RENAME TO categories_renamed"); // rolled back after the test

        mvc.perform(get("/api/admin/revenue"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Something went wrong"));
    }

    @Test
    void writeWithoutApiKeyReturns401() throws Exception {
        mvc.perform(post("/api/bookings/6/cancel")).andExpect(status().isUnauthorized());
    }

    @Test
    void writeWithWrongApiKeyReturns401() throws Exception {
        mvc.perform(post("/api/bookings/6/cancel").header(API_KEY_HEADER, "wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshViewsNeedsTheApiKey() throws Exception {
        mvc.perform(post("/api/admin/refresh-views")).andExpect(status().isUnauthorized());
    }
}
