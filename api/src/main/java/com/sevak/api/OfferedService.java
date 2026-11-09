package com.sevak.api.provider.dto;

import java.math.BigDecimal;

public record OfferedService(int serviceId, String serviceName, BigDecimal basePrice, BigDecimal price) {}
