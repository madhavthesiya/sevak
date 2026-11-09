package com.sevak.api.provider;

import com.sevak.api.auth.CurrentUser;
import com.sevak.api.common.OpenApiConfig;
import com.sevak.api.common.PageResponse;
import com.sevak.api.provider.dto.EarningsByMethod;
import com.sevak.api.provider.dto.JobSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** A provider's own work. The provider ID always comes from the login token. */
@RestController
@RequestMapping("/api/v1/provider")
@Tag(name = "3b. My work (provider)", description = "Your jobs and earnings")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class ProviderWorkController {

    private final ProviderService service;

    public ProviderWorkController(ProviderService service) {
        this.service = service;
    }

    @GetMapping("/jobs")
    @Operation(summary = "My jobs", description = "The street address is only shown once you have accepted the job.")
    public PageResponse<JobSummary> jobs(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(example = "pending")
                    @RequestParam(defaultValue = "pending")
                    @Pattern(regexp = "pending|confirmed|in_progress|completed|cancelled")
                    String status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        return service.jobs(CurrentUser.from(jwt).profileId(), status, page, limit);
    }

    @GetMapping("/earnings")
    @Operation(summary = "My earnings", description = "Successful payments by method, optionally between two dates.")
    public List<EarningsByMethod> earnings(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(example = "2026-04-01") @RequestParam(required = false) LocalDate from,
            @Parameter(example = "2026-04-30") @RequestParam(required = false) LocalDate to) {
        return service.earnings(CurrentUser.from(jwt).profileId(), from, to);
    }
}
