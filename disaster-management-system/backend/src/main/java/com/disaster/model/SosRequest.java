package com.disaster.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "sos_requests")
public class SosRequest {
    @Id
    private String id;
    private String userId;
    private String description;
    private String priority; // HIGH, NORMAL, MEDICAL
    private double latitude;
    private double longitude;
    @GeoSpatialIndexed(type = GeoSpatialIndexType.GEO_2DSPHERE)
    private GeoLocation geoLocation;
    private RescueStatus status;
    private Instant createdAt;

    public enum RescueStatus {
        PENDING, ASSIGNED, IN_PROGRESS, COMPLETED
    }

    public void syncGeo() {
        this.geoLocation = GeoLocation.of(latitude, longitude);
    }
}
