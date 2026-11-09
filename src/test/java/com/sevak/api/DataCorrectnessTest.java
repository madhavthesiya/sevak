package com.sevak.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sevak.api.repository.AdminRepository;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** The numbers the API reports are correct, and customers only see their own data. */
class DataCorrectnessTest extends IntegrationTest {

    @Autowired
    private AdminRepository admin;

    private BigDecimal totalCategoryRevenue() {
        return admin.getRevenueByCat().stream()
                .map(row -> (BigDecimal) row.get("total_revenue"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal totalSuccessfulPayments() {
        return jdbc.queryForObject("SELECT SUM(amount) FROM payments WHERE status = 'success'", BigDecimal.class);
    }

    private BigDecimal revenueOf(String category) {
        return admin.getRevenueByCat().stream()
                .filter(row -> category.equals(row.get("category_name")))
                .map(row -> (BigDecimal) row.get("total_revenue"))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void everyQueryInTheIntegrityCheckFileReturnsNoRows() throws Exception {
        String script = Files.readAllLines(Path.of("sql/checks/data_integrity.sql")).stream()
                .filter(line -> !line.startsWith("--"))
                .collect(Collectors.joining("\n"));
        List<String> queries = Arrays.stream(script.split(";"))
                .map(String::trim)
                .filter(sql -> sql.toUpperCase().startsWith("SELECT")
                        || sql.toUpperCase().startsWith("WITH"))
                .toList();

        assertThat(queries).hasSize(4);
        for (String sql : queries) {
            assertThat(jdbc.queryForList(sql)).as(sql).isEmpty();
        }
    }

    @Test
    void bookingWithTwoItemsInOneCategoryIsNotCountedTwice() {
        assertThat(revenueOf("Electrical")).isEqualByComparingTo("495.00");
        assertThat(revenueOf("Cleaning")).isEqualByComparingTo("4025.00");
        assertThat(revenueOf("Plumbing")).isEqualByComparingTo("1400.00");
        assertThat(revenueOf("Painting")).isEqualByComparingTo("5000.00");
        assertThat(totalCategoryRevenue()).isEqualByComparingTo(totalSuccessfulPayments());
    }

    @Test
    void paymentForAMultiCategoryBookingIsSplitBetweenCategories() {
        // Booking 9 is one ₹5000 Painting item; add a Cleaning item of the same value.
        jdbc.update("INSERT INTO booking_items (booking_id, item_no, service_id, quantity, unit_price)"
                + " VALUES (9, 2, 4, 1, 5000.00)");

        assertThat(revenueOf("Painting")).isEqualByComparingTo("2500.00");
        assertThat(revenueOf("Cleaning")).isEqualByComparingTo("6525.00");
        assertThat(totalCategoryRevenue()).isEqualByComparingTo(totalSuccessfulPayments());
    }

    @Test
    void liveLeaderboardMatchesTheMaterializedView() {
        List<Map<String, Object>> live = admin.getLeaderboard();

        assertThat(live).isNotEmpty();
        assertThat(admin.getLeaderboardFast()).isEqualTo(live);
    }

    @Test
    void providersWithNoReviewsHaveNoRating() {
        assertThat(jdbc.queryForObject(
                        "SELECT avg_rating FROM service_providers WHERE provider_id = 5", BigDecimal.class))
                .isNull();
    }

    @Test
    void spendingSeparatesPaidUpcomingAndRefunded() throws Exception {
        mvc.perform(get("/api/customers/3/spending"))
                .andExpect(jsonPath("$.paid").value(0))
                .andExpect(jsonPath("$.upcoming").value(350.00))
                .andExpect(jsonPath("$.refunded").value(350.00));
    }

    @Test
    void aPrepaidBookingIsPaidButNotUpcoming() throws Exception {
        jdbc.update(
                "INSERT INTO payments (booking_id, payment_method, status, amount) VALUES (4, 'UPI', 'success', 350)");

        mvc.perform(get("/api/customers/3/spending"))
                .andExpect(jsonPath("$.paid").value(350.00))
                .andExpect(jsonPath("$.upcoming").value(0));
    }

    @Test
    void searchHidesProvidersWhoAreNotVerified() throws Exception {
        mvc.perform(get("/api/general/services/search?q=fan"))
                .andExpect(jsonPath("$.length()").value(1));

        jdbc.update("UPDATE service_providers SET verification_status = 'pending' WHERE provider_id = 1");

        mvc.perform(get("/api/general/services/search?q=fan"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aCustomerCannotReadAnotherCustomersBookingItems() throws Exception {
        mvc.perform(get("/api/customers/2/bookings/1/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void theOwnerCanReadTheirBookingItems() throws Exception {
        mvc.perform(get("/api/customers/1/bookings/1/items"))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED) // REFRESH ... CONCURRENTLY can't run in a transaction
    void refreshViewsSucceedsWithTheApiKey() throws Exception {
        mvc.perform(post("/api/admin/refresh-views").header(API_KEY_HEADER, API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void everyShowcaseQueryRunsAgainstTheCurrentSchema() throws Exception {
        String script = Files.readAllLines(Path.of("sql/sevak_queries.sql")).stream()
                .filter(line -> !line.startsWith("--"))
                .collect(Collectors.joining("\n"));
        List<String> queries = Arrays.stream(script.split(";"))
                .map(String::trim)
                .filter(sql -> sql.toUpperCase().startsWith("SELECT"))
                .toList();

        assertThat(queries).hasSize(15);
        for (String sql : queries) {
            jdbc.queryForList(sql);
        }
    }

    @Test
    void aFailedPaymentAttemptIsNotCountedAsPaid() throws Exception {
        // Booking 1 has a failed attempt and then a successful one for 495.
        mvc.perform(get("/api/customers/1/spending"))
                .andExpect(jsonPath("$.total_bookings").value(2))
                .andExpect(jsonPath("$.paid").value(2495.00));
    }

    @Test
    void aBookingWithSeveralPaymentAttemptsAppearsOnceInCompletedJobs() throws Exception {
        mvc.perform(get("/api/providers/1/completed"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].payment_status").value("success"));
    }

    @Test
    void searchShowsTheProvidersOwnPrice() throws Exception {
        mvc.perform(get("/api/general/services/search?q=fan"))
                .andExpect(jsonPath("$[0].base_price").value(200.00))
                .andExpect(jsonPath("$[0].price").value(220.00));
    }

    @Test
    void addressesIncludeTheFlatNumber() throws Exception {
        mvc.perform(get("/api/customers/1/addresses"))
                .andExpect(jsonPath("$[0].house_no").exists());
    }

    @Test
    void aSoftDeletedAddressIsHidden() throws Exception {
        jdbc.update("UPDATE customer_addresses SET is_active = FALSE WHERE customer_id = 1 AND location_id = 1");

        mvc.perform(get("/api/customers/1/addresses"))
                .andExpect(jsonPath("$.length()").value(1));
    }
}
