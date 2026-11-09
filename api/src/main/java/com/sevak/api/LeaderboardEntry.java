package com.sevak.api.admin.dto;

import java.math.BigDecimal;

public record LeaderboardEntry(
        long rank,
        int providerId,
        String email,
        String cityName,
        BigDecimal avgRating,
        int reviewCount,
        BigDecimal weightedRating,
        long jobsCompleted,
        BigDecimal totalRevenue) {}
