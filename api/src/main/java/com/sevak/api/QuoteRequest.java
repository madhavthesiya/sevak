package com.sevak.api.booking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record QuoteRequest(
        @NotNull Integer providerId,
        @Size(max = 30) String couponCode,
        @NotEmpty @Size(max = 20) List<@Valid BookingItemRequest> items) {}
