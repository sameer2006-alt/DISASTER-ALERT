package com.disaster.controller;

import com.disaster.config.CorsConfig;
import com.disaster.config.CustomAccessDeniedHandler;
import com.disaster.config.CustomAuthenticationEntryPoint;
import com.disaster.config.IngestApiKeyFilter;
import com.disaster.config.JwtAuthenticationFilter;
import com.disaster.config.JwtService;
import com.disaster.config.SecurityConfig;
import com.disaster.dto.CreateRescueRequest;
import com.disaster.dto.CreateShelterRequest;
import com.disaster.dto.CreateVolunteerRequest;
import com.disaster.dto.OccupancyUpdateRequest;
import com.disaster.dto.OrgProfileUpdateRequest;
import com.disaster.dto.RescueStatusUpdateRequest;
import com.disaster.dto.VolunteerStatusUpdateRequest;
import com.disaster.model.Organisation;
import com.disaster.model.RescueRequest;
import com.disaster.model.Shelter;
import com.disaster.model.Volunteer;
import com.disaster.repository.OrganisationRepository;
import com.disaster.repository.RescueRequestRepository;
import com.disaster.repository.ShelterRepository;
import com.disaster.repository.VolunteerRepository;
import com.disaster.service.EmailService;
import com.disaster.service.OrganisationAuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(
        classes = {
                DtoSecurityAndValidationTest.TestConfig.class,
                SecurityConfig.class,
                CorsConfig.class,
                CustomAuthenticationEntryPoint.class,
                CustomAccessDeniedHandler.class,
                JwtAuthenticationFilter.class,
                IngestApiKeyFilter.class,
                ShelterController.class,
                VolunteerController.class,
                RescueController.class,
                OrgAuthController.class,
                GlobalExceptionHandler.class
        },
        properties = {
                "app.ingest.api-key=test-ingest-key-for-validation-suite"
        }
)
@AutoConfigureMockMvc
public class DtoSecurityAndValidationTest {

    @Configuration
    @org.springframework.web.servlet.config.annotation.EnableWebMvc
    static class TestConfig {
        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private SimpMessagingTemplate messagingTemplate;

    @MockBean
    private ShelterRepository shelterRepository;

    @MockBean
    private VolunteerRepository volunteerRepository;

    @MockBean
    private RescueRequestRepository rescueRepository;

    @MockBean
    private OrganisationRepository organisationRepository;

    @MockBean
    private OrganisationAuthService orgAuthService;

    @MockBean
    private EmailService emailService;

    @MockBean
    private MongoTemplate mongoTemplate;

    private static final String ORG_TOKEN = "valid-org-jwt";
    private static final String ORG_EMAIL = "relief@org.example";

    @BeforeEach
    void setUpAuth() {
        when(jwtService.isTokenValid(ORG_TOKEN)).thenReturn(true);
        when(jwtService.extractUsername(ORG_TOKEN)).thenReturn(ORG_EMAIL);
        when(jwtService.extractRole(ORG_TOKEN)).thenReturn("ORGANISATION");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Client-supplied id / verified / role are ignored and cannot overwrite
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Create shelter ignores client-supplied 'id' so MongoDB assigns new ID")
    void testCreateShelterIgnoresClientSuppliedId() throws Exception {
        Map<String, Object> body = Map.of(
                "id", "malicious-injected-shelter-id",
                "name", "Safe Haven Hub",
                "capacity", 200,
                "availableBeds", 150,
                "latitude", 19.076,
                "longitude", 72.877
        );

        when(shelterRepository.save(any(Shelter.class))).thenAnswer(inv -> {
            Shelter entity = inv.getArgument(0);
            assertNull(entity.getId(), "Entity to save must have null id so Mongo cannot overwrite existing documents");
            return Shelter.builder()
                    .id("generated-mongo-id-123")
                    .name(entity.getName())
                    .capacity(entity.getCapacity())
                    .availableBeds(entity.getAvailableBeds())
                    .latitude(entity.getLatitude())
                    .longitude(entity.getLongitude())
                    .build();
        });

        mockMvc.perform(post("/api/shelters")
                        .header("Authorization", "Bearer " + ORG_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("generated-mongo-id-123"))
                .andExpect(jsonPath("$.name").value("Safe Haven Hub"));

        ArgumentCaptor<Shelter> captor = ArgumentCaptor.forClass(Shelter.class);
        verify(shelterRepository).save(captor.capture());
        assertNull(captor.getValue().getId(), "Saved entity must ignore client-supplied id");
    }

    @Test
    @DisplayName("Create volunteer ignores client-supplied 'id' and 'userId'")
    void testCreateVolunteerIgnoresClientSuppliedIdAndUserId() throws Exception {
        Map<String, Object> body = Map.of(
                "id", "attacker-vol-id",
                "userId", "target-user-victim",
                "name", "Sam Fisher",
                "role", "Scout",
                "contact", "+919800011122",
                "latitude", 19.076,
                "longitude", 72.877
        );

        when(volunteerRepository.save(any(Volunteer.class))).thenAnswer(inv -> {
            Volunteer entity = inv.getArgument(0);
            assertNull(entity.getId(), "Volunteer to save must have null id");
            assertNull(entity.getUserId(), "Volunteer to save must have null userId");
            return Volunteer.builder()
                    .id("vol-gen-777")
                    .name(entity.getName())
                    .role(entity.getRole())
                    .contact(entity.getContact())
                    .latitude(entity.getLatitude())
                    .longitude(entity.getLongitude())
                    .build();
        });

        mockMvc.perform(post("/api/volunteers")
                        .header("Authorization", "Bearer " + ORG_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("vol-gen-777"))
                .andExpect(jsonPath("$.name").value("Sam Fisher"))
                .andExpect(jsonPath("$.contact").value("+919800011122"));

        ArgumentCaptor<Volunteer> captor = ArgumentCaptor.forClass(Volunteer.class);
        verify(volunteerRepository).save(captor.capture());
        assertNull(captor.getValue().getId());
        assertNull(captor.getValue().getUserId());
    }

    @Test
    @DisplayName("Create SOS rescue request ignores client-supplied 'id', 'userId', and 'status'")
    void testCreateRescueIgnoresClientSuppliedPrivilegedFields() throws Exception {
        Map<String, Object> body = Map.of(
                "id", "victim-rescue-id",
                "userId", "admin-user",
                "status", "COMPLETED",
                "description", "Stranded in vehicle on highway",
                "latitude", 19.076,
                "longitude", 72.877
        );

        when(rescueRepository.save(any(RescueRequest.class))).thenAnswer(inv -> {
            RescueRequest entity = inv.getArgument(0);
            assertNull(entity.getId(), "Rescue to save must have null id");
            assertNull(entity.getUserId(), "Rescue to save must have null userId");
            assertEquals(RescueRequest.RescueStatus.PENDING, entity.getStatus(), "Status must be PENDING");
            return RescueRequest.builder()
                    .id("rescue-gen-999")
                    .description(entity.getDescription())
                    .latitude(entity.getLatitude())
                    .longitude(entity.getLongitude())
                    .status(entity.getStatus())
                    .createdAt(entity.getCreatedAt())
                    .build();
        });

        mockMvc.perform(post("/api/rescue/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("rescue-gen-999"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        ArgumentCaptor<RescueRequest> captor = ArgumentCaptor.forClass(RescueRequest.class);
        verify(rescueRepository).save(captor.capture());
        assertNull(captor.getValue().getId());
        assertNull(captor.getValue().getUserId());
        assertEquals(RescueRequest.RescueStatus.PENDING, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("Update org profile ignores 'id', 'verified', 'role', and 'password'")
    void testUpdateOrgProfileIgnoresPrivilegedFields() throws Exception {
        Map<String, Object> body = Map.of(
                "id", "overwritten-id",
                "verified", true,
                "role", "ROLE_ADMIN",
                "verificationBadge", "SUPER_ADMIN",
                "password", "malicious-new-password",
                "organisationName", "Updated Relief Network",
                "description", "Providing food and medical supplies"
        );

        Organisation mockUpdated = Organisation.builder()
                .id("legit-org-id")
                .organisationName("Updated Relief Network")
                .email(ORG_EMAIL)
                .verified(false)
                .verificationBadge(null)
                .description("Providing food and medical supplies")
                .build();

        when(orgAuthService.updateProfile(eq(ORG_EMAIL), any(OrgProfileUpdateRequest.class)))
                .thenReturn(mockUpdated);

        mockMvc.perform(put("/api/org/profile")
                        .header("Authorization", "Bearer " + ORG_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organisationName").value("Updated Relief Network"));

        ArgumentCaptor<OrgProfileUpdateRequest> captor = ArgumentCaptor.forClass(OrgProfileUpdateRequest.class);
        verify(orgAuthService).updateProfile(eq(ORG_EMAIL), captor.capture());
        OrgProfileUpdateRequest captured = captor.getValue();
        assertEquals("Updated Relief Network", captured.organisationName());
        assertEquals("Providing food and medical supplies", captured.description());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Validation errors return 400 with field names
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Create shelter with invalid payload returns 400 with field names")
    void testCreateShelterValidationFails() throws Exception {
        Map<String, Object> invalid = Map.of(
                "name", "",                     // Blank name
                "capacity", 0,                  // Min 1
                "availableBeds", -10,           // Negative beds
                "latitude", 200.0,              // Out of range (-90 to 90)
                "longitude", 500.0              // Out of range (-180 to 180)
        );

        mockMvc.perform(post("/api/shelters")
                        .header("Authorization", "Bearer " + ORG_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.name", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors.capacity", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors.availableBeds", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors.latitude", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors.longitude", notNullValue()));
    }

    @Test
    @DisplayName("Create volunteer with missing required fields returns 400 with field names")
    void testCreateVolunteerValidationFails() throws Exception {
        Map<String, Object> invalid = Map.of(
                "name", "",
                "role", "",
                "contact", "",
                "latitude", 120.0
                // missing longitude
        );

        mockMvc.perform(post("/api/volunteers")
                        .header("Authorization", "Bearer " + ORG_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.name", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors.role", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors.contact", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors.latitude", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors.longitude", notNullValue()));
    }

    @Test
    @DisplayName("Create SOS request with blank description and out-of-range coordinates returns 400")
    void testCreateRescueValidationFails() throws Exception {
        Map<String, Object> invalid = Map.of(
                "description", "",
                "latitude", -150.0,
                "longitude", 300.0
        );

        mockMvc.perform(post("/api/rescue/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.description", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors.latitude", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors.longitude", notNullValue()));
    }

    @Test
    @DisplayName("Update shelter occupancy with negative beds returns 400 with field name")
    void testUpdateOccupancyValidationFails() throws Exception {
        Map<String, Object> invalid = Map.of("availableBeds", -5);

        mockMvc.perform(patch("/api/shelters/shelter-123/occupancy")
                        .header("Authorization", "Bearer " + ORG_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.availableBeds", notNullValue()));
    }
}
