package com.sevak.api.booking.dto;

public record IdempotencyRecord(String requestHash, Integer bookingId) {}
