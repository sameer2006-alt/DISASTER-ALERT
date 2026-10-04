package com.disaster.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "organisations")
public class Organisation {
    @Id
    private String id;
    private String organisationName;
    @Indexed(unique = true)
    private String email;
    private String password;
    private boolean verified;
    private String logoUrl;
    private String description;
    private String country;
    private String state;
    private String city;
    private String headquartersLocation;
    private List<String> operatingLocations = new ArrayList<>();
    private List<String> supportTypes = new ArrayList<>();
    private int shelterCapacity;
    private int foodCapacity;
    private int medicalCapacity;
    private boolean activeStatus;
    private double latitude;
    private double longitude;
    @GeoSpatialIndexed(type = GeoSpatialIndexType.GEO_2DSPHERE)
    private GeoLocation geoLocation;
    private String contactNumber;
    private String website;
    private List<String> resourcesAvailable = new ArrayList<>();
    private String verificationBadge; // VERIFIED, TRUSTED, GOVERNMENT
    private Instant createdAt;

    public void syncGeo() {
        if (latitude != 0 || longitude != 0) {
            this.geoLocation = GeoLocation.of(latitude, longitude);
        }
    }
}
