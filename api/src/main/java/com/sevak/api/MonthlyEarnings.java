package com.sevak.api.provider.dto;

import java.math.BigDecimal;

public record MonthlyEarnings(String month, long totalPayments, BigDecimal totalEarned) {}
