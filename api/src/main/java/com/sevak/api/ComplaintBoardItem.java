package com.sevak.api.admin.dto;

import java.time.OffsetDateTime;

public record ComplaintBoardItem(
        int complaintId,
        int bookingId,
        String subject,
        String description,
        String status,
        String raisedByName,
        String cityName,
        OffsetDateTime createdAt,
        String assignedAdminName,
        String resolutionNotes) {}
