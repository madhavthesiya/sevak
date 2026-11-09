package com.sevak.api.admin;

import com.sevak.api.admin.dto.AdminBooking;
import com.sevak.api.admin.dto.CategoryRevenue;
import com.sevak.api.admin.dto.CityRevenue;
import com.sevak.api.admin.dto.ComplaintSummary;
import com.sevak.api.admin.dto.LeaderboardEntry;
import com.sevak.api.admin.dto.RefreshResult;
import com.sevak.api.common.KeysetPage;
import com.sevak.api.common.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "5. Admin", description = "Revenue, leaderboard, complaints and all bookings")
public class AdminController {

    private final AdminService service;

    public AdminController(AdminService service) {
        this.service = service;
    }

    @GetMapping("/revenue")
    @Operation(summary = "Revenue by category")
    public List<CategoryRevenue> revenue() {
        return service.revenueByCategory();
    }

    @GetMapping("/leaderboard")
    @Operation(summary = "Provider leaderboard", description = "Ranked by Bayesian weighted rating.")
    public List<LeaderboardEntry> leaderboard(
            @Parameter(description = "Read the pre-computed materialized view instead of the live query")
                    @RequestParam(defaultValue = "false")
                    boolean fast) {
        return service.leaderboard(fast);
    }

    @GetMapping("/complaints")
    @Operation(summary = "Complaint summary")
    public List<ComplaintSummary> complaints() {
        return service.complaintSummary();
    }

    @GetMapping("/city-revenue")
    @Operation(summary = "Revenue by city", description = "Read from a materialized view.")
    public List<CityRevenue> cityRevenue() {
        return service.cityRevenue();
    }

    @GetMapping("/bookings")
    @Operation(
            summary = "All bookings",
            description = "Keyset pagination: pass nextBefore and nextBeforeId from the previous page.")
    public KeysetPage<AdminBooking> bookings(
            @RequestParam(required = false) OffsetDateTime before,
            @RequestParam(required = false) Integer beforeId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return service.bookings(before, beforeId, limit);
    }

    @PostMapping("/refresh-views")
    @SecurityRequirement(name = OpenApiConfig.API_KEY_SCHEME)
    @Operation(summary = "Refresh materialized views", description = "These also refresh automatically on a schedule.")
    public RefreshResult refreshViews() {
        return service.refreshViews();
    }
}
