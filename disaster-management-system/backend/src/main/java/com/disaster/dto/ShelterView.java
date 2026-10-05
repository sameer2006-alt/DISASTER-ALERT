package com.disaster.dto;

import com.disaster.model.GeoLocation;
import com.disaster.model.Shelter;

public record ShelterView(
        String id,
        String name,
        String organisationId,
        int capacity,
        int availableBeds,
        boolean foodAvailable,
        boolean medicalAvailable,
        double latitude,
        double longitude,
        GeoLocation geoLocation,
        String contactDetails,
        Shelter.ShelterStatus status
) {
    public static ShelterView from(Shelter s) {
        if (s == null) return null;
        return new ShelterView(
                s.getId(),
                s.getName(),
                s.getOrganisationId(),
                s.getCapacity(),
                s.getAvailableBeds(),
                s.isFoodAvailable(),
                s.isMedicalAvailable(),
                s.getLatitude(),
                s.getLongitude(),
                s.getGeoLocation(),
                s.getContactDetails(),
                s.getStatus()
        );
    }
}

