package com.sevak.api.booking;

import com.sevak.api.booking.dto.BookingActionResult;
import com.sevak.api.booking.dto.CancelRequest;
import com.sevak.api.common.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
@Tag(name = "4. Bookings", description = "Change a booking's status through PostgreSQL stored procedures")
@SecurityRequirement(name = OpenApiConfig.API_KEY_SCHEME)
public class BookingController {

    private final BookingService service;

    public BookingController(BookingService service) {
        this.service = service;
    }

    @PostMapping("/{id}/cancel")
    @Operation(
            summary = "Cancel a booking",
            description = "Allowed while pending or confirmed. A successful payment gets a pending refund.")
    public BookingActionResult cancel(
            @Parameter(example = "6") @PathVariable int id, @Valid @RequestBody CancelRequest request) {
        return service.cancel(id, request.reason());
    }

    @PostMapping("/{id}/accept")
    @Operation(
            summary = "Accept a booking",
            description = "The provider must offer every item and be available for the whole job.")
    public BookingActionResult accept(@Parameter(example = "10") @PathVariable int id) {
        return service.accept(id);
    }
}
