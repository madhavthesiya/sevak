package com.sevak.api.provider.dto;

import java.math.BigDecimal;

public record ProviderBasics(
        int providerId,
        String providerName,
        String cityName,
        String bio,
        Integer experienceYears,
        String verificationStatus,
        BigDecimal avgRating,
        int reviewCount,
        BigDecimal cityAverageRating,
        String comparedToCity) {}
