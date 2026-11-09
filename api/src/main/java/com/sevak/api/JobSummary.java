package com.sevak.api.provider.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record JobSummary(
        int bookingId,
        String customerName,
        OffsetDateTime scheduledStart,
        OffsetDateTime scheduledEnd,
        String status,
        BigDecimal totalAmount,
        String street,
        String areaName,
        String paymentStatus) {}
