package com.sevak.api.catalog.dto;

import java.math.BigDecimal;

public record ServiceItem(
        int serviceId, String categoryName, String serviceName, BigDecimal basePrice, String description) {}
