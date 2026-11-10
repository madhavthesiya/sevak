package com.sevak.api.customer;

import com.sevak.api.auth.CurrentUser;
import com.sevak.api.common.OpenApiConfig;
import com.sevak.api.common.PageResponse;
import com.sevak.api.customer.dto.BookingDetail;
import com.sevak.api.customer.dto.BookingSummary;
import com.sevak.api.customer.dto.NewAddressRequest;
import com.sevak.api.customer.dto.PaymentRecord;
import com.sevak.api.customer.dto.SavedAddress;
import com.sevak.api.customer.dto.SpendingSummary;
import com.sevak.api.meta.ShowsQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

/** Every path is "me": the customer comes from the token, so nobody can ask for someone else's data. */
@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "3. My account (customer)", description = "Your bookings, payments, spending and addresses")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class CustomerController {

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    private static int customerId(Jwt jwt) {
        return CurrentUser.from(jwt).profileId();
    }

    @GetMapping("/bookings")
    @ShowsQuery("me.bookings")
    @Operation(summary = "My bookings", description = "Newest first.")
    public PageResponse<BookingSummary> bookings(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        return service.bookings(customerId(jwt), page, limit);
    }

    @GetMapping("/bookings/{bookingId}")
    @ShowsQuery("me.booking")
    @Operation(summary = "Booking detail", description = "Address, price breakdown, items and status timeline.")
    public BookingDetail booking(@AuthenticationPrincipal Jwt jwt, @PathVariable int bookingId) {
        return service.booking(customerId(jwt), bookingId);
    }

    @GetMapping("/payments")
    @Operation(summary = "My payments", description = "Every payment attempt, including failed ones.")
    public PageResponse<PaymentRecord> payments(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        return service.payments(customerId(jwt), page, limit);
    }

    @GetMapping("/spending")
    @Operation(summary = "My spending", description = "Booking counts, and amounts paid, upcoming and refunded.")
    public SpendingSummary spending(@AuthenticationPrincipal Jwt jwt) {
        return service.spending(customerId(jwt));
    }

    @GetMapping("/addresses")
    @ShowsQuery("me.addresses")
    @Operation(summary = "My saved addresses")
    public List<SavedAddress> addresses(@AuthenticationPrincipal Jwt jwt) {
        return service.addresses(customerId(jwt));
    }

    @PostMapping("/addresses")
    @ShowsQuery("me.addresses")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an address", description = "Returns all saved addresses.")
    public List<SavedAddress> addAddress(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody NewAddressRequest request) {
        return service.addAddress(customerId(jwt), request);
    }

    @DeleteMapping("/addresses/{locationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove an address", description = "Past bookings at this address aren't affected.")
    public void removeAddress(@AuthenticationPrincipal Jwt jwt, @PathVariable int locationId) {
        service.removeAddress(customerId(jwt), locationId);
    }
}
