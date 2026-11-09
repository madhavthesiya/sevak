package com.sevak.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ComplaintUpdateRequest(
        @NotBlank @Pattern(regexp = "open|in_progress|resolved|closed")
        String status,

        Integer assignedAdminId,
        @Size(max = 2000) String resolutionNotes) {}
