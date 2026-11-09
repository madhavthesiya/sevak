package com.sevak.api.provider.dto;

import java.math.BigDecimal;

public record ProviderOffering(
        int providerId,
        String providerName,
        String cityName,
        int serviceId,
        String serviceName,
        BigDecimal basePrice,
        BigDecimal price,
        BigDecimal avgRating,
        int reviewCount,
        Integer experienceYears) {}
