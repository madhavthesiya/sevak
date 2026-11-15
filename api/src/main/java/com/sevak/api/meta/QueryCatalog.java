package com.sevak.api.meta;

import com.sevak.api.admin.AdminRepository;
import com.sevak.api.booking.BookingRepository;
import com.sevak.api.catalog.CatalogRepository;
import com.sevak.api.customer.CustomerRepository;
import com.sevak.api.provider.ProviderRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The allow-list of queries the app can show and explain. The SQL comes from the same constants the
 * repositories run, so what the drawer shows can't drift from what the database receives.
 */
@Component
public class QueryCatalog {

    /**
     * @param explainParams sample values for EXPLAIN, fixed here so no user input ever reaches it; null
     *     for statements that write, which must never be run for a demo
     */
    public record Entry(
            String key,
            String title,
            List<String> techniques,
            String description,
            String sql,
            Map<String, Object> explainParams) {}

    private static final String EXCLUSION_CONSTRAINT = """
            -- Guard on the table: two open bookings for one provider can never overlap.
            ALTER TABLE bookings ADD CONSTRAINT ex_provider_no_double_booking
                EXCLUDE USING gist (
                    provider_id WITH =,
                    tstzrange(scheduled_start, scheduled_end, '[)') WITH &&
                ) WHERE (status IN ('pending', 'confirmed', 'in_progress'));

            -- The insert that relies on it:
            """;

    private static final String AVAILABILITY_CONSTRAINT = """
            -- Guard on the table: one provider's slots on a day can never overlap.
            CONSTRAINT ex_availability_no_overlap
                EXCLUDE USING gist (provider_id WITH =, day_of_week WITH =,
                                    timerange(start_time, end_time) WITH &&)

            -- The insert that relies on it:
            """;

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public QueryCatalog() {
        add(
                "services.list",
                "Service catalogue",
                List.of("JOIN", "Offset pagination"),
                "Every service with its category, cheapest first within each category.",
                CatalogRepository.SERVICES_SQL,
                params("category", null, "limit", 100, "offset", 0));
        add(
                "providers.search",
                "Provider search",
                List.of("Materialized view", "Bayesian rating", "ILIKE", "EXISTS"),
                "Finds verified providers for a service and city, ranked by the leaderboard's stored Bayesian"
                        + " score, so one 5-star review can't outrank fifty excellent ones.",
                ProviderRepository.SEARCH_SQL,
                params("service", "%clean%", "city", "Ahmedabad", "day", 1, "limit", 10, "offset", 0));
        add(
                "providers.top",
                "Top rated providers",
                List.of("Materialized view", "RANK() window function", "Bayesian rating"),
                "Reads the stored leaderboard, keeping only verified providers. It is refreshed every 15 minutes.",
                ProviderRepository.TOP_SQL,
                params("limit", 5));
        add(
                "providers.profile",
                "Provider profile",
                List.of("Derived table", "CASE"),
                "A derived table works out each city's average rating, then compares this provider with it.",
                ProviderRepository.BASICS_SQL,
                params("id", 1));
        add(
                "providers.reviews",
                "Provider reviews",
                List.of("Owner's-rights view", "Row-level security", "Offset pagination"),
                "Newest reviews first, showing each customer's name. Bookings are private under row-level"
                        + " security, so this reads v_public_reviews, a view that runs with its owner's rights and"
                        + " shows only what this page shows.",
                ProviderRepository.REVIEWS_SQL,
                params("id", 1, "limit", 10, "offset", 0));
        add(
                "providers.slots",
                "Free time slots",
                List.of("generate_series", "LATERAL", "Range overlap (&&)", "SECURITY DEFINER function"),
                "Expands weekly working hours into 30-minute start times, then removes any that would overlap an"
                        + " open booking. Uses the same rule as the constraint that guards the table. Row-level"
                        + " security hides other customers' bookings, so the busy times come from"
                        + " fn_provider_busy_times, which returns only time ranges.",
                ProviderRepository.SLOTS_SQL,
                params("provider", 1, "from", LocalDate.now(), "days", 14, "minutes", 60));
        add(
                "bookings.price",
                "Live price",
                List.of("LEFT JOIN", "COALESCE"),
                "Today's price for a line. A variant's price wins over the provider's own price.",
                BookingRepository.PRICE_ITEM_SQL,
                params("provider", 1, "service", 1, "variant", 1));
        add(
                "bookings.create",
                "Create a booking",
                List.of("Exclusion constraint", "Advisory lock", "Idempotency key"),
                "Runs in one transaction. If two people take the same time, PostgreSQL refuses the second insert.",
                EXCLUSION_CONSTRAINT + BookingRepository.INSERT_BOOKING_SQL,
                null);
        add(
                "me.bookings",
                "My bookings",
                List.of("STRING_AGG", "Correlated subquery", "Row-level security"),
                "Your bookings, newest first, with the services on each one joined into a line of text. Row-level"
                        + " security limits the rows to yours as well, so a missing WHERE couldn't show anyone else's.",
                CustomerRepository.BOOKINGS_SQL,
                params("id", 1, "limit", 20, "offset", 0));
        add(
                "me.booking",
                "Booking detail",
                List.of("LATERAL join", "EXISTS", "Row-level security"),
                "One booking with its latest payment attempt. Filters on the customer too, and row-level security"
                        + " would hide another person's booking even without that filter.",
                CustomerRepository.BOOKING_HEADER_SQL,
                params("booking", 1, "customer", 1));
        add(
                "me.addresses",
                "Saved addresses",
                List.of("BCNF decomposition", "Soft delete"),
                "A physical point lives in locations; your label and flat number live in customer_addresses.",
                CustomerRepository.ADDRESSES_SQL,
                params("id", 1));
        add(
                "provider.jobs",
                "Job inbox",
                List.of("LATERAL join", "CASE", "STRING_AGG"),
                "A LATERAL join picks each job's latest payment. The street stays hidden until the job is accepted.",
                ProviderRepository.JOBS_SQL.formatted("ASC"),
                params("id", 1, "status", "pending", "limit", 10, "offset", 0));
        add(
                "provider.earnings",
                "Earnings by method",
                List.of("GROUP BY", "SUM", "Time zone conversion"),
                "Successful payments per payment method, using India dates.",
                ProviderRepository.EARNINGS_SQL,
                params("id", 1, "from", null, "to", null));
        add(
                "provider.earningsMonthly",
                "Earnings by month",
                List.of("to_char month grouping", "GROUP BY", "Time zone conversion"),
                "Successful payments grouped by the month they were paid in, using India dates.",
                ProviderRepository.MONTHLY_EARNINGS_SQL,
                params("id", 1));
        add(
                "provider.availability",
                "Working hours",
                List.of("Exclusion constraint", "Custom range type"),
                "Overlapping hours are refused by the database, and the API turns that into a friendly message.",
                AVAILABILITY_CONSTRAINT + ProviderRepository.INSERT_AVAILABILITY_SQL,
                null);
        add(
                "provider.services",
                "Services offered",
                List.of("JOIN", "Soft delete"),
                "What this provider sells and at what price.",
                ProviderRepository.SERVICES_SQL,
                params("id", 1));
        add(
                "admin.revenue",
                "Revenue by category",
                List.of("Materialized view", "CTE", "Window function", "Proportional split"),
                "Reads a stored summary rebuilt every 15 minutes, because calculating it live took 4.7 seconds at"
                        + " 1M bookings. A window function splits each payment across its categories by item value,"
                        + " so a mixed booking isn't counted twice.",
                AdminRepository.CATEGORY_REVENUE_SQL + "\n-- The stored copy is rebuilt every 15 minutes by:\n"
                        + AdminRepository.REVENUE_SQL
                                .strip()
                                .lines()
                                .map(l -> "-- " + l)
                                .reduce((a, b) -> a + "\n" + b)
                                .orElse(""),
                params());
        add(
                "admin.cityRevenue",
                "Revenue by city",
                List.of("Materialized view"),
                "Reads a pre-computed summary that refreshes every 15 minutes.",
                AdminRepository.CITY_REVENUE_SQL,
                params());
        add(
                "admin.leaderboard",
                "Leaderboard, live",
                List.of("View", "RANK() window function", "Bayesian rating", "SECURITY DEFINER function"),
                "Computes the ranking from the bookings and reviews on every request. fn_live_leaderboard checks"
                        + " that you're an admin, then reads the view as its owner: under row-level security the"
                        + " planner priced a check on every payment and the page took 4-5 s instead of under 1 s.",
                AdminRepository.LEADERBOARD_LIVE_SQL,
                params());
        add(
                "admin.leaderboard.fast",
                "Leaderboard, materialized",
                List.of("Materialized view", "REFRESH CONCURRENTLY", "SECURITY DEFINER function"),
                "The same ranking stored ahead of time. Faster to read, but only as fresh as the last refresh."
                        + " It holds each provider's email and revenue, so admins read it through"
                        + " fn_stored_leaderboard; search and the home page can read only the public columns.",
                AdminRepository.LEADERBOARD_FAST_SQL,
                params());
        add(
                "admin.complaints",
                "Complaints board",
                List.of("Multi-table JOIN", "LEFT JOIN"),
                "Every complaint with who raised it and who is handling it. The summary uses STRING_AGG.",
                AdminRepository.COMPLAINT_BOARD_SQL,
                params());
        add(
                "admin.verification",
                "Verification queue",
                List.of("STRING_AGG", "LEFT JOIN", "GROUP BY"),
                "Providers waiting for a decision, with their submitted documents joined into a line of text.",
                AdminRepository.VERIFICATION_SQL,
                params("status", "pending"));
    }

    private void add(
            String key,
            String title,
            List<String> techniques,
            String description,
            String sql,
            Map<String, Object> explainParams) {
        entries.put(key, new Entry(key, title, techniques, description, sql.strip(), explainParams));
    }

    /** A HashMap because sample values may be null, which Map.of refuses. */
    private static Map<String, Object> params(Object... pairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    public Optional<Entry> find(String key) {
        return Optional.ofNullable(entries.get(key));
    }

    public List<String> keys() {
        return List.copyOf(entries.keySet());
    }
}
