package com.disaster.dto;

import com.disaster.model.GeoLocation;
import com.disaster.model.Volunteer;

import java.util.List;

public record VolunteerOrgView(
        String id,
        String name,
        String role,
        String contact,
        List<String> skills,
        Volunteer.VolunteerStatus status,
        String assignedDisasterId,
        double latitude,
        double longitude,
        GeoLocation geoLocation
) {
    public static VolunteerOrgView from(Volunteer v) {
        if (v == null) return null;
        return new VolunteerOrgView(
                v.getId(),
                v.getName(),
                v.getRole(),
                v.getContact(),
                v.getSkills() != null ? v.getSkills() : List.of(),
                v.getStatus(),
                v.getAssignedDisasterId(),
                v.getLatitude(),
                v.getLongitude(),
                v.getGeoLocation()
        );
    }
}

