package com.sevak.api.customer.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record BookingSummary(
        int bookingId,
        String providerName,
        OffsetDateTime scheduledStart,
        OffsetDateTime scheduledEnd,
        String status,
        BigDecimal totalAmount,
        String services) {}
