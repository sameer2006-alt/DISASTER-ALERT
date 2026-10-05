package com.disaster.dto;

import com.disaster.model.GeoLocation;
import com.disaster.model.Volunteer;

import java.util.List;

public record VolunteerView(
        String id,
        String name,
        String role,
        List<String> skills,
        Volunteer.VolunteerStatus status,
        String assignedDisasterId,
        double latitude,
        double longitude,
        GeoLocation geoLocation
) {
    public static VolunteerView from(Volunteer v) {
        if (v == null) return null;
        return new VolunteerView(
                v.getId(),
                v.getName(),
                v.getRole(),
                v.getSkills() != null ? v.getSkills() : List.of(),
                v.getStatus(),
                v.getAssignedDisasterId(),
                v.getLatitude(),
                v.getLongitude(),
                v.getGeoLocation()
        );
    }
}

