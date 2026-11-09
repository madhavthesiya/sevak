package com.sevak.api.customer.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PaymentRecord(
        int bookingId,
        String paymentMethod,
        String status,
        BigDecimal amount,
        String gatewayRef,
        OffsetDateTime paidAt,
        String failureReason) {}
