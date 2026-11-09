package com.sevak.api.booking.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record BookingItemRequest(
        @NotNull Integer serviceId,
        Integer variantId,
        @NotNull @Min(1) @Max(20) Integer quantity) {}
