package com.disaster.dto;

import com.disaster.model.RescueRequest;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RescueStatusUpdateRequest(
        @NotNull(message = "status is required")
        RescueRequest.RescueStatus status
) {}

