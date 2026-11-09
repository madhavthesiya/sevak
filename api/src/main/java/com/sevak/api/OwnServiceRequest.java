package com.sevak.api.provider.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record OwnServiceRequest(
        @NotNull Integer serviceId,
        @NotNull @DecimalMin("1") @DecimalMax("999999") BigDecimal price) {}
