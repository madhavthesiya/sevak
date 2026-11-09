package com.sevak.api.provider.dto;

import java.time.LocalTime;

public record AvailabilitySlot(String dayOfWeek, LocalTime startTime, LocalTime endTime) {}
