package com.disaster.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrgProfileUpdateRequest(
        @Size(max = 200, message = "organisationName cannot exceed 200 characters")
        String organisationName,

        @Size(max = 2000, message = "description cannot exceed 2000 characters")
        String description,

        @Size(max = 500, message = "logoUrl cannot exceed 500 characters")
        String logoUrl,

        String country,
        String state,
        String city,
        String headquartersLocation,
        List<String> operatingLocations,
        List<String> supportTypes,
        List<String> resourcesAvailable,

        @Min(value = 0, message = "shelterCapacity cannot be negative")
        Integer shelterCapacity,

        @Min(value = 0, message = "foodCapacity cannot be negative")
        Integer foodCapacity,

        @Min(value = 0, message = "medicalCapacity cannot be negative")
        Integer medicalCapacity,

        Boolean activeStatus,

        @DecimalMin(value = "-90.0", message = "latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "latitude must be between -90 and 90")
        Double latitude,

        @DecimalMin(value = "-180.0", message = "longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "longitude must be between -180 and 180")
        Double longitude,

        String contactNumber,
        String website
) {}

