package com.sevak.api.booking.dto;

import java.math.BigDecimal;

public record PaymentResult(int paymentId, String status, BigDecimal amount, String gatewayRef) {}
