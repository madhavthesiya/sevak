package com.sevak.api.provider;

import com.sevak.api.common.PageResponse;
import com.sevak.api.provider.dto.ProviderOffering;
import com.sevak.api.provider.dto.ProviderProfile;
import com.sevak.api.provider.dto.ProviderReview;
import com.sevak.api.provider.dto.TopProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/providers")
@Tag(name = "2. Providers", description = "Find providers and see their profiles")
public class ProviderController {

    private final ProviderService service;

    public ProviderController(ProviderService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Search providers",
            description =
                    "One row per verified provider and service they offer, best rated first. All filters are optional.")
    public PageResponse<ProviderOffering> search(
            @Parameter(description = "Part of a service name", example = "clean")
                    @RequestParam(required = false)
                    @Size(max = 100)
                    String service,
            @Parameter(example = "Ahmedabad") @RequestParam(required = false) @Size(max = 100) String city,
            @Parameter(description = "Available on this day, any letter case", example = "Monday")
                    @RequestParam(required = false)
                    String day,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        return this.service.search(service, city, day, page, limit);
    }

    @GetMapping("/top")
    @Operation(summary = "Top providers", description = "Verified providers ranked by Bayesian weighted rating.")
    public List<TopProvider> top(@RequestParam(defaultValue = "5") @Min(1) @Max(50) int limit) {
        return service.top(limit);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Provider profile",
            description = "Rating vs city average, services with prices, and weekly availability.")
    public ProviderProfile profile(@Parameter(example = "1") @PathVariable int id) {
        return service.profile(id);
    }

    @GetMapping("/{id}/reviews")
    @Operation(summary = "Provider reviews", description = "Newest first.")
    public PageResponse<ProviderReview> reviews(
            @Parameter(example = "1") @PathVariable int id,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        return service.reviews(id, page, limit);
    }
}
