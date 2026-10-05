package com.disaster.dto;

import com.disaster.model.Volunteer;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateVolunteerRequest {

    @NotBlank(message = "name is required")
    private String name;

    @NotBlank(message = "role is required")
    private String role;

    @NotBlank(message = "contact is required")
    private String contact;

    private List<String> skills;

    @NotNull(message = "latitude is required")
    @DecimalMin(value = "-90.0", message = "latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "latitude must be between -90 and 90")
    private Double latitude;

    @NotNull(message = "longitude is required")
    @DecimalMin(value = "-180.0", message = "longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "longitude must be between -180 and 180")
    private Double longitude;

    private Volunteer.VolunteerStatus status;
    private String assignedDisasterId;

    public Volunteer toEntity() {
        Volunteer volunteer = Volunteer.builder()
                .name(this.name)
                .role(this.role)
                .contact(this.contact)
                .skills(this.skills != null ? this.skills : List.of())
                .status(this.status != null ? this.status : Volunteer.VolunteerStatus.AVAILABLE)
                .latitude(this.latitude != null ? this.latitude : 0.0)
                .longitude(this.longitude != null ? this.longitude : 0.0)
                .assignedDisasterId(this.assignedDisasterId)
                .build();
        volunteer.syncGeo();
        return volunteer;
    }
}

