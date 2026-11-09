package com.sevak.api.controller;

import com.sevak.api.repository.GeneralRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/general")
@Tag(name = "1. General", description = "Browse services, search providers, check availability")
public class GeneralController {

    private final GeneralRepository repo;

    public GeneralController(GeneralRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/services")
    @Operation(
            summary = "Browse all services",
            description = "List all services grouped by category with base prices. Supports pagination.")
    public ResponseEntity<List<Map<String, Object>>> getAllServices(
            @Parameter(description = "Page number (1-indexed)") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "Results per page") @RequestParam(defaultValue = "10") @Min(1) @Max(100)
                    int limit) {
        return ResponseEntity.ok(repo.getAllServices(page, limit));
    }

    @GetMapping("/services/search")
    @Operation(
            summary = "Search providers by service",
            description =
                    "Find active providers offering a service matching the keyword. Uses 5-table JOIN with ILIKE. Supports pagination.")
    public ResponseEntity<List<Map<String, Object>>> searchProviders(
            @Parameter(description = "Service name keyword (e.g. cleaning, fan, painting)") @RequestParam String q,
            @Parameter(description = "Page number (1-indexed)") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "Results per page") @RequestParam(defaultValue = "10") @Min(1) @Max(100)
                    int limit) {
        return ResponseEntity.ok(repo.searchProviders(q, page, limit));
    }

    @GetMapping("/providers/top")
    @Operation(
            summary = "Top rated providers",
            description = "Returns the highest-rated verified providers across all cities. Defaults to top 5.")
    public ResponseEntity<List<Map<String, Object>>> getTopProviders(
            @Parameter(description = "Number of top providers to return")
                    @RequestParam(defaultValue = "5")
                    @Min(1)
                    @Max(50)
                    int top) {
        return ResponseEntity.ok(repo.getTopProviders(top));
    }

    @GetMapping("/providers/available")
    @Operation(
            summary = "Providers available on a day",
            description =
                    "List all active providers available on the specified day with their time slots. Supports pagination.")
    public ResponseEntity<List<Map<String, Object>>> getAvailableProviders(
            @Parameter(description = "Day of week, any letter case (e.g. Monday, monday, MONDAY)") @RequestParam
                    String day,
            @Parameter(description = "Page number (1-indexed)") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "Results per page") @RequestParam(defaultValue = "10") @Min(1) @Max(100)
                    int limit) {
        return ResponseEntity.ok(repo.getAvailableProviders(toDbDay(day), page, limit));
    }

    /**
     * Spring's default String-to-enum conversion is case-sensitive, so we parse the day ourselves.
     * The database stores days as 'Monday', 'Tuesday', ... (see the CHECK constraint on provider_availability).
     */
    private static String toDbDay(String day) {
        DayOfWeek parsed;
        try {
            parsed = DayOfWeek.valueOf(day.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid day '" + day + "'. Use a day name such as Monday.");
        }
        String name = parsed.name();
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }
}
