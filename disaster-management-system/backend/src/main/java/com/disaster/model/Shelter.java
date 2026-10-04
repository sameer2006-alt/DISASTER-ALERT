package com.disaster.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "shelters")
public class Shelter {
    @Id
    private String id;
    private String name;
    private String organisationId;
    private int capacity;
    private int availableBeds;
    private boolean foodAvailable;
    private boolean medicalAvailable;
    private double latitude;
    private double longitude;
    @GeoSpatialIndexed(type = GeoSpatialIndexType.GEO_2DSPHERE)
    private GeoLocation geoLocation;
    private String contactDetails;
    private ShelterStatus status;

    public void syncGeo() {
        this.geoLocation = GeoLocation.of(latitude, longitude);
    }

    public enum ShelterStatus {
        INACTIVE, ACTIVE, FULL
    }
}
