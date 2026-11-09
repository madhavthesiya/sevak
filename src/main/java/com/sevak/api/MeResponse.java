package com.sevak.api.auth.dto;

public record MeResponse(int userId, String email, String fullName, String phone, String role) {}
