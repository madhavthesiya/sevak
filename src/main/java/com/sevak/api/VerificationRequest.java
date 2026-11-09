package com.sevak.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerificationRequest(
        @NotBlank @Pattern(regexp = "verified|rejected") String status) {}
