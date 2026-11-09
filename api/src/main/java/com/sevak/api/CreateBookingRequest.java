package com.sevak.api.booking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

public record CreateBookingRequest(
        @NotNull Integer providerId,
        @NotNull Integer locationId,
        @Size(max = 30) String couponCode,
        @NotNull @Future OffsetDateTime scheduledStart,
        @NotEmpty @Size(max = 20) List<@Valid BookingItemRequest> items,
        @Size(max = 1000) String specialInstructions) {}
