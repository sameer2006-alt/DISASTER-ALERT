package com.disaster.dto;

import com.disaster.model.Shelter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateShelterRequest {

    @NotBlank(message = "name is required")
    private String name;

    @NotNull(message = "capacity is required")
    @Min(value = 1, message = "capacity must be at least 1")
    private Integer capacity;

    @NotNull(message = "availableBeds is required")
    @Min(value = 0, message = "availableBeds cannot be negative")
    private Integer availableBeds;

    private Boolean foodAvailable;
    private Boolean medicalAvailable;

    @NotNull(message = "latitude is required")
    @DecimalMin(value = "-90.0", message = "latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "latitude must be between -90 and 90")
    private Double latitude;

    @NotNull(message = "longitude is required")
    @DecimalMin(value = "-180.0", message = "longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "longitude must be between -180 and 180")
    private Double longitude;

    private String contactDetails;
    private Shelter.ShelterStatus status;
    private String organisationId;

    public Shelter toEntity() {
        Shelter shelter = Shelter.builder()
                .name(this.name)
                .capacity(this.capacity != null ? this.capacity : 0)
                .availableBeds(this.availableBeds != null ? this.availableBeds : 0)
                .foodAvailable(Boolean.TRUE.equals(this.foodAvailable))
                .medicalAvailable(Boolean.TRUE.equals(this.medicalAvailable))
                .latitude(this.latitude != null ? this.latitude : 0.0)
                .longitude(this.longitude != null ? this.longitude : 0.0)
                .contactDetails(this.contactDetails)
                .status(this.status != null ? this.status : Shelter.ShelterStatus.INACTIVE)
                .organisationId(this.organisationId)
                .build();
        shelter.syncGeo();
        return shelter;
    }
}

