package com.sevak.api.provider;

import com.sevak.api.auth.CurrentUser;
import com.sevak.api.common.OpenApiConfig;
import com.sevak.api.common.PageResponse;
import com.sevak.api.meta.ShowsQuery;
import com.sevak.api.provider.dto.EarningsByMethod;
import com.sevak.api.provider.dto.JobSummary;
import com.sevak.api.provider.dto.MonthlyEarnings;
import com.sevak.api.provider.dto.NewAvailabilityRequest;
import com.sevak.api.provider.dto.OfferedService;
import com.sevak.api.provider.dto.OwnAvailabilitySlot;
import com.sevak.api.provider.dto.OwnServiceRequest;
import com.sevak.api.provider.dto.ProviderProfile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** A provider's own work. The provider ID always comes from the login token. */
@RestController
@RequestMapping("/api/v1/provider")
@Tag(name = "3b. My work (provider)", description = "Your jobs, earnings, services and working hours")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class ProviderWorkController {

    private final ProviderService service;

    public ProviderWorkController(ProviderService service) {
        this.service = service;
    }

    @GetMapping("/profile")
    @ShowsQuery("providers.profile")
    @Operation(summary = "My profile", description = "Your rating compared with your city, as customers see it.")
    public ProviderProfile profile(@AuthenticationPrincipal Jwt jwt) {
        return service.profile(CurrentUser.from(jwt).profileId());
    }

    @GetMapping("/services")
    @ShowsQuery("provider.services")
    @Operation(summary = "My services")
    public List<OfferedService> services(@AuthenticationPrincipal Jwt jwt) {
        return service.ownServices(CurrentUser.from(jwt).profileId());
    }

    @PostMapping("/services")
    @ShowsQuery("provider.services")
    @Operation(
            summary = "Offer a service or change its price",
            description = "Sending a service again updates its price.")
    public void offerService(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody OwnServiceRequest request) {
        service.offerService(CurrentUser.from(jwt).profileId(), request);
    }

    @DeleteMapping("/services/{serviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Stop offering a service", description = "Past bookings keep their prices.")
    public void stopOffering(@AuthenticationPrincipal Jwt jwt, @PathVariable int serviceId) {
        service.stopOffering(CurrentUser.from(jwt).profileId(), serviceId);
    }

    @GetMapping("/availability")
    @ShowsQuery("provider.availability")
    @Operation(summary = "My working hours", description = "Days are numbers: 1 is Monday, 7 is Sunday.")
    public List<OwnAvailabilitySlot> availability(@AuthenticationPrincipal Jwt jwt) {
        return service.ownAvailability(CurrentUser.from(jwt).profileId());
    }

    @PostMapping("/availability")
    @ResponseStatus(HttpStatus.CREATED)
    @ShowsQuery("provider.availability")
    @Operation(summary = "Add working hours", description = "Hours that overlap existing ones return 409 slot-overlap.")
    public OwnAvailabilitySlot addAvailability(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody NewAvailabilityRequest request) {
        return service.addAvailability(CurrentUser.from(jwt).profileId(), request);
    }

    @DeleteMapping("/availability/{slotId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove working hours")
    public void removeAvailability(@AuthenticationPrincipal Jwt jwt, @PathVariable long slotId) {
        service.removeAvailability(CurrentUser.from(jwt).profileId(), slotId);
    }

    @GetMapping("/jobs")
    @ShowsQuery("provider.jobs")
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

    @GetMapping("/earnings/monthly")
    @ShowsQuery("provider.earningsMonthly")
    @Operation(summary = "My earnings by month")
    public List<MonthlyEarnings> monthlyEarnings(@AuthenticationPrincipal Jwt jwt) {
        return service.monthlyEarnings(CurrentUser.from(jwt).profileId());
    }

    @GetMapping("/earnings")
    @ShowsQuery("provider.earnings")
    @Operation(summary = "My earnings", description = "Successful payments by method, optionally between two dates.")
    public List<EarningsByMethod> earnings(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(example = "2026-04-01") @RequestParam(required = false) LocalDate from,
            @Parameter(example = "2026-04-30") @RequestParam(required = false) LocalDate to) {
        return service.earnings(CurrentUser.from(jwt).profileId(), from, to);
    }
}
