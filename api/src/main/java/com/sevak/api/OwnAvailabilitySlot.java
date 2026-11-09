package com.sevak.api.provider.dto;

import java.time.LocalTime;

public record OwnAvailabilitySlot(long slotId, int dayOfWeek, LocalTime startTime, LocalTime endTime) {}
