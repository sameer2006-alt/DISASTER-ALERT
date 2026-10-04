package com.disaster.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "disasters")
public class DisasterEvent {
    @Id
    private String id;
    private DisasterType disasterType;
    private int severity;
    private String location;
    private double latitude;
    private double longitude;
    @GeoSpatialIndexed(type = GeoSpatialIndexType.GEO_2DSPHERE)
    private GeoLocation geoLocation;
    private Instant timestamp;
    private String message;
    private double affectedRadius;
    private EventSource source;
    @Builder.Default
    private boolean active = true;

    // SACHET NDMA enrichment — populated only for SACHET_NDMA sourced events
    private String state;
    private String officialSeverity;
    private String sourceUrl;
    private String sachetId;
    private String rawDisasterType;

    public void syncGeo() {
        this.geoLocation = GeoLocation.of(latitude, longitude);
    }
}
