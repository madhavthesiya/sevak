package com.sevak.api.admin.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AdminBooking(
        int bookingId,
        String customerName,
        String providerName,
        OffsetDateTime scheduledStart,
        String status,
        BigDecimal totalAmount) {}
