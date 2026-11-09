package com.sevak.api.customer.dto;

import java.math.BigDecimal;

public record SpendingSummary(
        long totalBookings,
        long completed,
        long cancelled,
        long pending,
        BigDecimal paid,
        BigDecimal upcoming,
        BigDecimal refunded) {}
