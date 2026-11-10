package com.sevak.api.catalog;

import com.sevak.api.catalog.dto.AreaOption;
import com.sevak.api.catalog.dto.ServiceItem;
import com.sevak.api.common.PageResponse;
import com.sevak.api.meta.ShowsQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "1. Services", description = "The service catalogue")
public class CatalogController {

    private final CatalogService service;

    public CatalogController(CatalogService service) {
        this.service = service;
    }

    @GetMapping("/areas")
    @Operation(summary = "Cities and areas", description = "Every area with its city, for search and address forms.")
    public List<AreaOption> areas() {
        return service.areas();
    }

    @GetMapping("/services")
    @ShowsQuery("services.list")
    @Operation(summary = "List services", description = "All services, optionally for one category.")
    public PageResponse<ServiceItem> listServices(
            @Parameter(example = "Cleaning") @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        return service.listServices(category, page, limit);
    }
}
