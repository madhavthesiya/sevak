package com.sevak.api.booking.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record BookingCreated(
        int bookingId,
        String status,
        OffsetDateTime scheduledStart,
        OffsetDateTime scheduledEnd,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal totalAmount) {}
