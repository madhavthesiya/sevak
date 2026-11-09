package com.sevak.api.admin.dto;

import java.math.BigDecimal;

public record CityRevenue(
        String cityName,
        long totalBookings,
        long uniqueCustomers,
        BigDecimal totalRevenue,
        BigDecimal avgBookingValue) {}
