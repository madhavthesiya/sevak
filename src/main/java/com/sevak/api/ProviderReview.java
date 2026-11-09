package com.sevak.api.provider.dto;

import java.time.OffsetDateTime;

public record ProviderReview(int rating, String comment, String customerName, OffsetDateTime reviewedAt) {}
