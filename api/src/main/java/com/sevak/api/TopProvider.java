package com.sevak.api.provider.dto;

import java.math.BigDecimal;

public record TopProvider(
        int providerId,
        String providerName,
        String cityName,
        BigDecimal avgRating,
        int reviewCount,
        BigDecimal weightedRating,
        Integer experienceYears) {}
