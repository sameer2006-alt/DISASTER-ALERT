package com.disaster.dto;

import com.disaster.model.DisasterEvent;
import com.disaster.model.DisasterType;
import com.disaster.model.EventSource;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Data Transfer Object for disaster event ingestion via POST /api/events/ingest.
 * Enforces strict validation on all required attributes:
 * - disasterType: non-null, known DisasterType enum
 * - severity: integer between 1 and 10
 * - latitude: decimal between -90.0 and 90.0
 * - longitude: decimal between -180.0 and 180.0
 * - message: non-blank, max 500 characters
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DisasterIngestDto {

    private String id;

    @NotNull(message = "disasterType is required (FLOOD, FIRE, EARTHQUAKE, CYCLONE, LANDSLIDE)")
    @JsonAlias({"type", "disaster_type"})
    private DisasterType disasterType;

    @NotNull(message = "severity is required")
    @Min(value = 1, message = "severity must be between 1 and 10")
    @Max(value = 10, message = "severity must be between 1 and 10")
    private Integer severity;

    private String location;

    @NotNull(message = "latitude is required")
    @DecimalMin(value = "-90.0", message = "latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "latitude must be between -90 and 90")
    private Double latitude;

    @NotNull(message = "longitude is required")
    @DecimalMin(value = "-180.0", message = "longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "longitude must be between -180 and 180")
    private Double longitude;

    @NotBlank(message = "message is required")
    @Size(max = 500, message = "message cannot exceed 500 characters")
    private String message;

    private Double affectedRadius;
    private EventSource source;
    private Boolean active;
    private Instant timestamp;

    // Optional metadata fields from SACHET NDMA or external ingestion services
    private String state;
    private String officialSeverity;
    private String sourceUrl;
    private String sachetId;
    private String rawDisasterType;
    private String author;

    public DisasterEvent toEntity() {
        EventSource resolvedSource = this.source != null ? this.source :
                ((this.sachetId != null || this.sourceUrl != null) ? EventSource.SACHET_NDMA : EventSource.MANUAL);

        String resolvedLocation = (this.location != null && !this.location.isBlank())
                ? this.location
                : (this.state != null && !this.state.isBlank() ? this.state + ", India" : "Unknown Location");

        Instant resolvedTimestamp = this.timestamp != null ? this.timestamp : Instant.now();
        double resolvedRadius = (this.affectedRadius != null && this.affectedRadius > 0) ? this.affectedRadius : 25.0;
        boolean resolvedActive = this.active == null || this.active;

        return DisasterEvent.builder()
                .id(this.id)
                .disasterType(this.disasterType)
                .severity(this.severity != null ? this.severity : 5)
                .location(resolvedLocation)
                .latitude(this.latitude != null ? this.latitude : 0.0)
                .longitude(this.longitude != null ? this.longitude : 0.0)
                .timestamp(resolvedTimestamp)
                .message(this.message)
                .affectedRadius(resolvedRadius)
                .source(resolvedSource)
                .active(resolvedActive)
                .state(this.state)
                .officialSeverity(this.officialSeverity)
                .sourceUrl(this.sourceUrl)
                .sachetId(this.sachetId)
                .rawDisasterType(this.rawDisasterType)
                .build();
    }
}
