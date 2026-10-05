package com.disaster.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OccupancyUpdateRequest(
        @NotNull(message = "availableBeds is required")
        @Min(value = 0, message = "availableBeds cannot be negative")
        Integer availableBeds
) {}

