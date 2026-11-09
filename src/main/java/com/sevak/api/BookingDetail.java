package com.sevak.api.customer.dto;

import java.util.List;

public record BookingDetail(BookingHeader booking, List<BookingLine> items, List<StatusChange> timeline) {}
