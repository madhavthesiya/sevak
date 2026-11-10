package com.sevak.api.provider.dto;

import java.util.List;

public record ProviderProfile(
        ProviderBasics provider,
        List<OfferedService> services,
        List<AvailabilitySlot> availability,
        List<OfferedVariant> variants) {}
