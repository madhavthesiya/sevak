package com.sevak.api.customer.dto;

import java.time.OffsetDateTime;

public record StatusChange(String oldStatus, String newStatus, OffsetDateTime changedAt, String remarks) {}
