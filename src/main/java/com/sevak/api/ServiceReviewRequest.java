package com.sevak.api.booking.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ServiceReviewRequest(
        @NotNull Integer serviceId,
        @NotNull @Min(1) @Max(5) Integer rating,
        @Size(max = 2000) String comment) {}
