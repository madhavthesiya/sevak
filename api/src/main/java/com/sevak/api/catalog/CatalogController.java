package com.sevak.api.catalog;

import com.sevak.api.catalog.dto.ServiceItem;
import com.sevak.api.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/services")
@Tag(name = "1. Services", description = "The service catalogue")
public class CatalogController {

    private final CatalogService service;

    public CatalogController(CatalogService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List services", description = "All services, optionally for one category.")
    public PageResponse<ServiceItem> listServices(
            @Parameter(example = "Cleaning") @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        return service.listServices(category, page, limit);
    }
}
