package com.sevak.api.customer.dto;

public record SavedAddress(
        int locationId,
        String label,
        String houseNo,
        String street,
        String landmark,
        String areaName,
        String pincode,
        String cityName) {}
