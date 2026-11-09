package com.sevak.api.booking.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record BookingParties(
        int customerId, int providerId, String status, OffsetDateTime scheduledStart, BigDecimal totalAmount) {}
