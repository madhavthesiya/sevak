package com.sevak.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "sevak.rate-limit.strict-per-minute=2")
class RateLimitTest extends IntegrationTest {

    private org.springframework.test.web.servlet.ResultActions attemptLogin() throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody@example.com\",\"password\":\"whatever\"}"));
    }

    @Test
    void tooManyLoginsAreBlockedButBrowsingStillWorks() throws Exception {
        attemptLogin().andExpect(status().isUnauthorized());
        attemptLogin().andExpect(status().isUnauthorized());

        attemptLogin()
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.type").value("urn:sevak:error:rate-limited"));

        mvc.perform(get("/api/v1/services")).andExpect(status().isOk());
    }
}
