package com.disaster.dto;

import com.disaster.model.RescueRequest;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateRescueRequest {

    @NotBlank(message = "description is required")
    private String description;

    private String priority;

    @NotNull(message = "latitude is required")
    @DecimalMin(value = "-90.0", message = "latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "latitude must be between -90 and 90")
    private Double latitude;

    @NotNull(message = "longitude is required")
    @DecimalMin(value = "-180.0", message = "longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "longitude must be between -180 and 180")
    private Double longitude;

    public RescueRequest toEntity() {
        RescueRequest req = RescueRequest.builder()
                .description(this.description)
                .priority(this.priority != null && !this.priority.isBlank() ? this.priority : "HIGH")
                .latitude(this.latitude != null ? this.latitude : 0.0)
                .longitude(this.longitude != null ? this.longitude : 0.0)
                .status(RescueRequest.RescueStatus.PENDING)
                .createdAt(Instant.now())
                .build();
        req.syncGeo();
        return req;
    }
}

