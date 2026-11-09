package com.sevak.api.admin.dto;

import java.math.BigDecimal;

public record CategoryRevenue(String categoryName, long totalBookings, BigDecimal totalRevenue) {}
