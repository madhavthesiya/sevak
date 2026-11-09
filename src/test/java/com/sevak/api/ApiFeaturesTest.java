package com.sevak.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sevak.api.admin.AdminService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Pagination, search filters, profiles, request IDs and operational endpoints. */
class ApiFeaturesTest extends IntegrationTest {

    @Autowired
    private AdminService adminService;

    @Autowired
    private DataSource dataSource;

    @Test
    void pagesReportTheirTotals() throws Exception {
        mvc.perform(get("/api/v1/services?limit=5&page=2"))
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.limit").value(5))
                .andExpect(jsonPath("$.total").value(12))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void walkingThroughPagesNeverRepeatsARow() throws Exception {
        List<Integer> seen = new ArrayList<>();
        for (int page = 1; page <= 4; page++) {
            String body = mvc.perform(get("/api/v1/services?limit=3&page=" + page))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            seen.addAll(JsonPath.read(body, "$.data[*].serviceId"));
        }
        assertThat(seen).hasSize(12).doesNotHaveDuplicates();
    }

    @Test
    void servicesCanBeFilteredByCategoryInAnyCase() throws Exception {
        mvc.perform(get("/api/v1/services?category=cLeAnInG"))
                .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void providerSearchCombinesItsFilters() throws Exception {
        mvc.perform(get("/api/v1/providers?city=ahmedabad"))
                .andExpect(jsonPath("$.total").value(4));
        mvc.perform(get("/api/v1/providers?service=clean&city=Ahmedabad&day=Friday"))
                .andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/api/v1/providers?service=clean&day=Tuesday"))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void aPercentSignInSearchIsNotAWildcard() throws Exception {
        mvc.perform(get("/api/v1/providers").param("service", "%"))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void publicProviderDataHasNoEmailOrPhone() throws Exception {
        for (String url : List.of("/api/v1/providers", "/api/v1/providers/top", "/api/v1/providers/1")) {
            mvc.perform(get(url))
                    .andExpect(jsonPath("$..email").isEmpty())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                            .string(not(containsString("@gmail.com"))));
        }
    }

    @Test
    void theProfileCombinesRatingServicesAndSlots() throws Exception {
        mvc.perform(get("/api/v1/providers/1"))
                .andExpect(jsonPath("$.provider.providerName").value("Ravi Kumar"))
                .andExpect(jsonPath("$.provider.comparedToCity").value("Above Average"))
                .andExpect(jsonPath("$.services.length()").value(2))
                .andExpect(jsonPath("$.availability[0].dayOfWeek").value("Monday"))
                .andExpect(jsonPath("$.availability[0].startTime").value("09:00:00"));
    }

    @Test
    void earningsCanBeLimitedToADateRange() throws Exception {
        mvc.perform(get("/api/v1/providers/1/earnings?from=2026-04-01&to=2026-04-30"))
                .andExpect(jsonPath("$[0].totalEarned").value(495.00));
        mvc.perform(get("/api/v1/providers/1/earnings?from=2026-05-01"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void keysetPaginationVisitsEveryBookingOnceNewestFirst() throws Exception {
        List<Integer> ids = new ArrayList<>();
        String url = "/api/v1/admin/bookings?limit=3";
        boolean more = true;
        while (more) {
            String body = mvc.perform(get(url))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            ids.addAll(JsonPath.read(body, "$.data[*].bookingId"));
            more = JsonPath.read(body, "$.hasMore");
            if (more) {
                String before = JsonPath.read(body, "$.nextBefore");
                int beforeId = JsonPath.read(body, "$.nextBeforeId");
                url = "/api/v1/admin/bookings?limit=3&before=" + before.replace("+", "%2B") + "&beforeId=" + beforeId;
            }
        }
        assertThat(ids).containsExactly(10, 9, 8, 7, 6, 5, 4, 3, 2, 1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void aRefreshIsSkippedWhileAnotherInstanceHoldsTheLock() throws Exception {
        try (Connection other = dataSource.getConnection();
                PreparedStatement lock = other.prepareStatement("SELECT pg_advisory_lock(734001)");
                PreparedStatement unlock = other.prepareStatement("SELECT pg_advisory_unlock(734001)")) {
            lock.execute();
            try {
                assertThat(adminService.refreshViews().refreshed()).isFalse();
            } finally {
                unlock.execute();
            }
        }
        assertThat(adminService.refreshViews().refreshed()).isTrue();
    }

    @Test
    void everyResponseHasARequestId() throws Exception {
        mvc.perform(get("/api/v1/services")).andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void aCallersSafeRequestIdIsKept() throws Exception {
        mvc.perform(get("/api/v1/services").header("X-Request-Id", "abc-123"))
                .andExpect(header().string("X-Request-Id", "abc-123"));
    }

    @Test
    void anUnsafeRequestIdIsReplaced() throws Exception {
        mvc.perform(get("/api/v1/services").header("X-Request-Id", "bad id\nforged log line"))
                .andExpect(header().string("X-Request-Id", not(containsString("forged"))));
    }

    @Test
    void readinessIsUpWhenTheDatabaseIsReachable() throws Exception {
        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void infoShowsTheBuildVersion() throws Exception {
        mvc.perform(get("/actuator/info")).andExpect(jsonPath("$.build.version").exists());
    }

    @Test
    void onlyTheIntendedActuatorEndpointsAreExposed() throws Exception {
        mvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
    }
}
