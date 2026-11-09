package com.sevak.api.provider.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record FreeSlot(LocalDate date, OffsetDateTime start) {}
