package com.sevak.api.admin;

import com.sevak.api.admin.dto.AdminActionResult;
import com.sevak.api.admin.dto.AdminBooking;
import com.sevak.api.admin.dto.CategoryRevenue;
import com.sevak.api.admin.dto.CityRevenue;
import com.sevak.api.admin.dto.ComplaintBoardItem;
import com.sevak.api.admin.dto.ComplaintSummary;
import com.sevak.api.admin.dto.ComplaintUpdateRequest;
import com.sevak.api.admin.dto.LeaderboardEntry;
import com.sevak.api.admin.dto.RefreshResult;
import com.sevak.api.admin.dto.VerificationCandidate;
import com.sevak.api.admin.dto.VerificationRequest;
import com.sevak.api.auth.CurrentUser;
import com.sevak.api.common.KeysetPage;
import com.sevak.api.common.OpenApiConfig;
import com.sevak.api.meta.QueryHeaderConfig;
import com.sevak.api.meta.ShowsQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "5. Admin", description = "Analytics, verification, complaints and refunds. Admin only.")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class AdminController {

    private final AdminService service;

    public AdminController(AdminService service) {
        this.service = service;
    }

    @GetMapping("/revenue")
    @ShowsQuery("admin.revenue")
    @Operation(summary = "Revenue by category")
    public List<CategoryRevenue> revenue() {
        return service.revenueByCategory();
    }

    @GetMapping("/leaderboard")
    @ShowsQuery("admin.leaderboard")
    @Operation(summary = "Provider leaderboard", description = "Ranked by Bayesian weighted rating.")
    public List<LeaderboardEntry> leaderboard(
            @Parameter(description = "Read the pre-computed materialized view instead of the live query")
                    @RequestParam(defaultValue = "false")
                    boolean fast,
            HttpServletResponse response) {
        if (fast) {
            response.setHeader(QueryHeaderConfig.HEADER, "admin.leaderboard.fast");
        }
        return service.leaderboard(fast);
    }

    @GetMapping("/complaints/board")
    @ShowsQuery("admin.complaints")
    @Operation(summary = "Every complaint", description = "Open complaints first.")
    public List<ComplaintBoardItem> complaintBoard() {
        return service.complaintBoard();
    }

    @GetMapping("/providers")
    @ShowsQuery("admin.verification")
    @Operation(summary = "Providers by verification status")
    public List<VerificationCandidate> providers(
            @RequestParam(defaultValue = "pending") @Pattern(regexp = "pending|verified|rejected") String status) {
        return service.providersByVerification(status);
    }

    @GetMapping("/complaints")
    @Operation(summary = "Complaint summary")
    public List<ComplaintSummary> complaints() {
        return service.complaintSummary();
    }

    @GetMapping("/city-revenue")
    @ShowsQuery("admin.cityRevenue")
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
    @Operation(summary = "Refresh materialized views", description = "These also refresh automatically on a schedule.")
    public RefreshResult refreshViews() {
        return service.refreshViews();
    }

    @PatchMapping("/providers/{id}/verification")
    @Operation(summary = "Verify or reject a provider", description = "Also updates their documents.")
    public AdminActionResult verifyProvider(
            @AuthenticationPrincipal Jwt jwt, @PathVariable int id, @Valid @RequestBody VerificationRequest request) {
        return service.verifyProvider(CurrentUser.from(jwt).profileId(), id, request.status());
    }

    @PatchMapping("/complaints/{id}")
    @Operation(summary = "Update a complaint", description = "Assign it, add notes, or resolve it.")
    public AdminActionResult updateComplaint(@PathVariable int id, @Valid @RequestBody ComplaintUpdateRequest request) {
        return service.updateComplaint(id, request);
    }

    @PostMapping("/refunds/{id}/process")
    @Operation(summary = "Mark a refund as processed", description = "Also marks its payment as refunded.")
    public AdminActionResult processRefund(@PathVariable int id) {
        return service.processRefund(id);
    }
}
