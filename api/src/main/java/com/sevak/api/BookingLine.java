package com.sevak.api.customer.dto;

import java.math.BigDecimal;

public record BookingLine(
        int itemNo, String serviceName, String variantName, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {}
