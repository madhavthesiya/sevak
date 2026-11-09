package com.sevak.api.booking;

import com.sevak.api.auth.CurrentUser;
import com.sevak.api.booking.dto.BookingActionResult;
import com.sevak.api.booking.dto.BookingCreated;
import com.sevak.api.booking.dto.CancelRequest;
import com.sevak.api.booking.dto.ComplaintCreated;
import com.sevak.api.booking.dto.ComplaintRequest;
import com.sevak.api.booking.dto.CreateBookingRequest;
import com.sevak.api.booking.dto.PaymentRequest;
import com.sevak.api.booking.dto.PaymentResult;
import com.sevak.api.booking.dto.ReviewRequest;
import com.sevak.api.booking.dto.ReviewResult;
import com.sevak.api.common.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
@Tag(name = "4. Bookings", description = "Create a booking and move it through its lifecycle")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class BookingController {

    private final BookingCreationService creation;
    private final BookingService service;

    public BookingController(BookingCreationService creation, BookingService service) {
        this.creation = creation;
        this.service = service;
    }

    @PostMapping
    @Operation(
            summary = "Create a booking",
            description =
                    "Customer only. Send an Idempotency-Key to retry safely; a repeat returns the same booking with 200.")
    public ResponseEntity<BookingCreated> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateBookingRequest request,
            @Parameter(description = "Any unique value for this attempt")
                    @RequestHeader(name = "Idempotency-Key", required = false)
                    @Size(max = 100)
                    String idempotencyKey) {
        BookingCreationService.Outcome outcome = creation.create(CurrentUser.from(jwt), request, idempotencyKey);
        return ResponseEntity.status(outcome.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(outcome.booking());
    }

    @PostMapping("/{id}/cancel")
    @Operation(
            summary = "Cancel a booking",
            description =
                    "Customers: until 2 hours before the start. Providers: until the start. A successful payment gets a pending refund.")
    public BookingActionResult cancel(
            @AuthenticationPrincipal Jwt jwt, @PathVariable int id, @Valid @RequestBody CancelRequest request) {
        return service.cancel(CurrentUser.from(jwt), id, request.reason());
    }

    @PostMapping("/{id}/accept")
    @Operation(summary = "Accept a booking", description = "Provider only.")
    public BookingActionResult accept(@AuthenticationPrincipal Jwt jwt, @PathVariable int id) {
        return service.accept(CurrentUser.from(jwt), id);
    }

    @PostMapping("/{id}/decline")
    @Operation(summary = "Decline a booking", description = "Provider only, while it is pending.")
    public BookingActionResult decline(@AuthenticationPrincipal Jwt jwt, @PathVariable int id) {
        return service.decline(CurrentUser.from(jwt), id);
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "Start the job", description = "Provider only.")
    public BookingActionResult start(@AuthenticationPrincipal Jwt jwt, @PathVariable int id) {
        return service.start(CurrentUser.from(jwt), id);
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Complete the job", description = "Provider only.")
    public BookingActionResult complete(@AuthenticationPrincipal Jwt jwt, @PathVariable int id) {
        return service.complete(CurrentUser.from(jwt), id);
    }

    @PostMapping("/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Pay for a booking", description = "A mock gateway; simulate can be success or failure.")
    public PaymentResult pay(
            @AuthenticationPrincipal Jwt jwt, @PathVariable int id, @Valid @RequestBody PaymentRequest request) {
        return service.pay(CurrentUser.from(jwt), id, request);
    }

    @PostMapping("/{id}/review")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Review a completed booking")
    public ReviewResult review(
            @AuthenticationPrincipal Jwt jwt, @PathVariable int id, @Valid @RequestBody ReviewRequest request) {
        return service.review(CurrentUser.from(jwt), id, request);
    }

    @PostMapping("/{id}/complaints")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Raise a complaint", description = "Either the customer or the provider of the booking.")
    public ComplaintCreated complain(
            @AuthenticationPrincipal Jwt jwt, @PathVariable int id, @Valid @RequestBody ComplaintRequest request) {
        return service.complain(CurrentUser.from(jwt), id, request);
    }
}
