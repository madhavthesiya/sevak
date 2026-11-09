package com.sevak.api.customer.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record NewAddressRequest(
        @NotNull Integer areaId,
        @NotBlank @Size(max = 255) String street,
        @Size(max = 255) String landmark,
        @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
        @Size(max = 50) String label,
        @Size(max = 50) String houseNo) {}
