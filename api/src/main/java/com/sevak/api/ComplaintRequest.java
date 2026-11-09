package com.sevak.api.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ComplaintRequest(
        @NotBlank @Size(max = 255) String subject,
        @Size(max = 2000) String description) {}
