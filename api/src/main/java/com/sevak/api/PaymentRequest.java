package com.sevak.api.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PaymentRequest(
        @NotBlank @Pattern(regexp = "UPI|Credit Card|Debit Card|Net Banking")
        String paymentMethod,

        @Pattern(regexp = "success|failure") String simulate) {}
