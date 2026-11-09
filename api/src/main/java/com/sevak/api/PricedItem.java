package com.sevak.api.booking.dto;

import java.math.BigDecimal;

public record PricedItem(BigDecimal unitPrice, int duration) {}
