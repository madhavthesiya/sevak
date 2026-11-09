package com.sevak.api.admin.dto;

public record VerificationCandidate(
        int providerId,
        String providerName,
        String cityName,
        String bio,
        Integer experienceYears,
        String verificationStatus,
        String documents) {}
