package com.sevak.api.auth.dto;

public record TokenResponse(String accessToken, String tokenType, long expiresInSeconds, String role) {}
