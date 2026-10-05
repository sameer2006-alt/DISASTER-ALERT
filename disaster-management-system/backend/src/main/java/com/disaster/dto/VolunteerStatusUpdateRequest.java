package com.disaster.dto;

import com.disaster.model.Volunteer;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VolunteerStatusUpdateRequest(
        @NotNull(message = "status is required")
        Volunteer.VolunteerStatus status
) {}

