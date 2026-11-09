package com.sevak.api.booking.dto;

import java.math.BigDecimal;

public record RedeemedCoupon(int couponId, String discountType, BigDecimal discountValue, BigDecimal maxDiscount) {}
