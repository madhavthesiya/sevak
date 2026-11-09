package com.sevak.api.provider.dto;

import java.math.BigDecimal;

public record OfferedVariant(int serviceId, int variantId, String variantName, BigDecimal price, int duration) {}
