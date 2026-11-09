package com.sevak.api.provider.dto;

import java.math.BigDecimal;

public record EarningsByMethod(String paymentMethod, long totalPayments, BigDecimal totalEarned) {}
