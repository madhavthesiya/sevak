package com.sevak.api.customer;

import com.sevak.api.common.PageResponse;
import com.sevak.api.customer.dto.BookingDetail;
import com.sevak.api.customer.dto.BookingSummary;
import com.sevak.api.customer.dto.PaymentRecord;
import com.sevak.api.customer.dto.SavedAddress;
import com.sevak.api.customer.dto.SpendingSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers/{customerId}")
@Tag(name = "3. Customers", description = "A customer's bookings, payments, spending and addresses")
public class CustomerController {

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping("/bookings")
    @Operation(summary = "Bookings", description = "Newest first.")
    public PageResponse<BookingSummary> bookings(
            @Parameter(example = "1") @PathVariable int customerId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        return service.bookings(customerId, page, limit);
    }

    @GetMapping("/bookings/{bookingId}")
    @Operation(summary = "Booking detail", description = "Address, price breakdown, items and status timeline.")
    public BookingDetail booking(
            @Parameter(example = "1") @PathVariable int customerId,
            @Parameter(example = "1") @PathVariable int bookingId) {
        return service.booking(customerId, bookingId);
    }

    @GetMapping("/payments")
    @Operation(summary = "Payments", description = "Every payment attempt, including failed ones.")
    public PageResponse<PaymentRecord> payments(
            @Parameter(example = "1") @PathVariable int customerId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        return service.payments(customerId, page, limit);
    }

    @GetMapping("/spending")
    @Operation(summary = "Spending summary", description = "Booking counts, and amounts paid, upcoming and refunded.")
    public SpendingSummary spending(@Parameter(example = "1") @PathVariable int customerId) {
        return service.spending(customerId);
    }

    @GetMapping("/addresses")
    @Operation(summary = "Saved addresses")
    public List<SavedAddress> addresses(@Parameter(example = "1") @PathVariable int customerId) {
        return service.addresses(customerId);
    }
}
