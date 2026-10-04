package com.disaster.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "volunteers")
public class Volunteer {
    @Id
    private String id;
    private String userId;
    private String name;
    private String contact;
    private String role;
    private List<String> skills = new ArrayList<>();
    private VolunteerStatus status;
    private String assignedDisasterId;
    private double latitude;
    private double longitude;
    @GeoSpatialIndexed(type = GeoSpatialIndexType.GEO_2DSPHERE)
    private GeoLocation geoLocation;

    public enum VolunteerStatus {
        AVAILABLE, BUSY, ON_MISSION
    }

    public void syncGeo() {
        this.geoLocation = GeoLocation.of(latitude, longitude);
    }
}
