package com.sevak.api.booking.dto;

import java.math.BigDecimal;

public record BookingQuote(
        BigDecimal subtotal, BigDecimal discount, BigDecimal totalAmount, int minutes, String couponCode) {}
