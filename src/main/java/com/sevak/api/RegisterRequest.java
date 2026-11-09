package com.sevak.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 100) String fullName,
        @Size(max = 20) String phone,
        @NotBlank @Pattern(regexp = "customer|provider") String role,
        Integer cityId,
        @Size(max = 2000) String bio,
        @Min(0) @Max(80) Integer experienceYears) {}
