package com.disaster.dto;

import com.disaster.model.GeoLocation;
import com.disaster.model.RescueRequest;

import java.time.Instant;

public record RescueView(
        String id,
        String description,
        String priority,
        double latitude,
        double longitude,
        GeoLocation geoLocation,
        RescueRequest.RescueStatus status,
        Instant createdAt
) {
    public static RescueView from(RescueRequest r) {
        if (r == null) return null;
        return new RescueView(
                r.getId(),
                r.getDescription(),
                r.getPriority(),
                r.getLatitude(),
                r.getLongitude(),
                r.getGeoLocation(),
                r.getStatus(),
                r.getCreatedAt()
        );
    }
}

