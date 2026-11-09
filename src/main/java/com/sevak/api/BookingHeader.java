package com.sevak.api.customer.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record BookingHeader(
        int bookingId,
        String providerName,
        OffsetDateTime scheduledStart,
        OffsetDateTime scheduledEnd,
        String status,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal totalAmount,
        String couponCode,
        String specialInstructions,
        String houseNo,
        String street,
        String areaName,
        String cityName) {}
