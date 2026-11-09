package com.sevak.api.auth.dto;

public record UserCredentials(int userId, String passwordHash, String role, String status, Integer profileId) {}
