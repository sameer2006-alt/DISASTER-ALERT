package com.disaster.dto;

import com.disaster.model.GeoLocation;
import com.disaster.model.Organisation;

import java.time.Instant;
import java.util.List;

public record OrgPublicView(
        String id,
        String organisationName,
        String logoUrl,
        String description,
        String country,
        String state,
        String city,
        String headquartersLocation,
        List<String> operatingLocations,
        List<String> supportTypes,
        int shelterCapacity,
        int foodCapacity,
        int medicalCapacity,
        boolean activeStatus,
        double latitude,
        double longitude,
        GeoLocation geoLocation,
        String website,
        List<String> resourcesAvailable,
        String verificationBadge,
        boolean verified,
        Instant createdAt
) {
    public static OrgPublicView from(Organisation o) {
        if (o == null) return null;
        return new OrgPublicView(
                o.getId(),
                o.getOrganisationName(),
                o.getLogoUrl(),
                o.getDescription(),
                o.getCountry(),
                o.getState(),
                o.getCity(),
                o.getHeadquartersLocation(),
                o.getOperatingLocations() != null ? o.getOperatingLocations() : List.of(),
                o.getSupportTypes() != null ? o.getSupportTypes() : List.of(),
                o.getShelterCapacity(),
                o.getFoodCapacity(),
                o.getMedicalCapacity(),
                o.isActiveStatus(),
                o.getLatitude(),
                o.getLongitude(),
                o.getGeoLocation(),
                o.getWebsite(),
                o.getResourcesAvailable() != null ? o.getResourcesAvailable() : List.of(),
                o.getVerificationBadge(),
                o.isVerified(),
                o.getCreatedAt()
        );
    }
}

