package com.disaster.controller;

import com.disaster.config.*;
import com.disaster.dto.UserLocationUpdateRequest;
import com.disaster.model.GeoLocation;
import com.disaster.model.Organisation;
import com.disaster.model.User;
import com.disaster.model.UserRole;
import com.disaster.repository.OrganisationRepository;
import com.disaster.repository.UserRepository;
import com.disaster.service.*;
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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = {
                UserLocationAndRegistrationTest.TestConfig.class,
                SecurityConfig.class,
                CorsConfig.class,
                CustomAuthenticationEntryPoint.class,
                CustomAccessDeniedHandler.class,
                JwtAuthenticationFilter.class,
                IngestApiKeyFilter.class,
                UserController.class,
                GlobalExceptionHandler.class
        },
        properties = {
                "app.ingest.api-key=test-ingest-api-key-for-location-suite"
        }
)
@AutoConfigureMockMvc
public class UserLocationAndRegistrationTest {

    @Configuration
    @EnableWebMvc
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
    private UserRepository userRepository;

    @MockBean
    private OrganisationRepository organisationRepository;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @MockBean
    private OtpService otpService;

    @MockBean
    private EmailService emailService;

    private static final String CITIZEN_TOKEN = "valid-citizen-jwt";
    private static final String CITIZEN_USERNAME = "rahul_indore";

    @BeforeEach
    void setUpAuth() {
        when(jwtService.isTokenValid(CITIZEN_TOKEN)).thenReturn(true);
        when(jwtService.extractUsername(CITIZEN_TOKEN)).thenReturn(CITIZEN_USERNAME);
        when(jwtService.extractRole(CITIZEN_TOKEN)).thenReturn("CITIZEN");
        when(passwordEncoder.encode(any())).thenReturn("hashed-pwd");
        when(otpService.generateAndStore(any(), any(), any())).thenReturn("123456");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. AuthService Registration with Coordinates & Fallbacks
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Registering citizen with explicit coordinates stores them and computes 2dsphere GeoLocation")
    void testRegisterCitizenWithExplicitCoordinatesStoresThem() {
        GeoLocationLookupService lookupService = new GeoLocationLookupService();
        AuthService authService = new AuthService(
                userRepository, jwtService, passwordEncoder, otpService, emailService, lookupService);

        when(userRepository.findByEmail("delhi_citizen@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("delhi_citizen")).thenReturn(Optional.empty());

        AuthService.UserRegistrationData data = new AuthService.UserRegistrationData(
                "delhi_citizen", "delhi_citizen@example.com", "Password123#",
                "Connaught Place", "Delhi (NCT)", "Central Delhi",
                28.6500, 77.2300
        );

        authService.register(data);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertEquals(28.6500, saved.getLatitude(), 0.0001);
        assertEquals(77.2300, saved.getLongitude(), 0.0001);
        assertNotNull(saved.getGeoLocation());
        assertEquals("Point", saved.getGeoLocation().getType());
        assertEquals(77.2300, saved.getGeoLocation().getCoordinates()[0], 0.0001); // longitude first
        assertEquals(28.6500, saved.getGeoLocation().getCoordinates()[1], 0.0001); // latitude second
    }

    @Test
    @DisplayName("Registering citizen without coordinates falls back to Indore city centroid near 22.7, 75.9")
    void testRegisterCitizenWithoutCoordinatesFallsBackToCityCentroid() {
        GeoLocationLookupService lookupService = new GeoLocationLookupService();
        AuthService authService = new AuthService(
                userRepository, jwtService, passwordEncoder, otpService, emailService, lookupService);

        when(userRepository.findByEmail("indore_citizen@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("indore_citizen")).thenReturn(Optional.empty());

        // Null latitude & longitude provided
        AuthService.UserRegistrationData data = new AuthService.UserRegistrationData(
                "indore_citizen", "indore_citizen@example.com", "Password123#",
                "Vijay Nagar", "Madhya Pradesh", "Indore",
                null, null
        );

        authService.register(data);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        // Indore centroid must be near 22.7, 75.9 (specifically 22.7196, 75.8577)
        assertEquals(22.7196, saved.getLatitude(), 0.05, "Latitude must be near 22.7");
        assertEquals(75.8577, saved.getLongitude(), 0.05, "Longitude must be near 75.9");
        assertNotNull(saved.getGeoLocation());
        assertEquals(75.8577, saved.getGeoLocation().getCoordinates()[0], 0.001);
        assertEquals(22.7196, saved.getGeoLocation().getCoordinates()[1], 0.001);
    }

    @Test
    @DisplayName("Registering citizen with completely unknown location falls back to India centre (20.5937, 78.9629)")
    void testRegisterCitizenWithUnknownLocationFallsBackToIndiaCentre() {
        GeoLocationLookupService lookupService = new GeoLocationLookupService();
        AuthService authService = new AuthService(
                userRepository, jwtService, passwordEncoder, otpService, emailService, lookupService);

        when(userRepository.findByEmail("lost@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("lost_user")).thenReturn(Optional.empty());

        AuthService.UserRegistrationData data = new AuthService.UserRegistrationData(
                "lost_user", "lost@example.com", "Password123#",
                "Unknown Sector", "Fictional State", "Fictional City",
                null, null
        );

        authService.register(data);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertEquals(20.5937, saved.getLatitude(), 0.0001);
        assertEquals(78.9629, saved.getLongitude(), 0.0001);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Organisation Registration with Coordinates & Fallbacks
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Registering organisation without coordinates falls back to Indore city centroid near 22.7, 75.9")
    void testOrgRegisterWithoutCoordinatesFallsBackToCityCentroid() {
        GeoLocationLookupService lookupService = new GeoLocationLookupService();
        OrganisationAuthService orgAuthService = new OrganisationAuthService(
                organisationRepository, jwtService, passwordEncoder, otpService, emailService, lookupService);

        when(organisationRepository.findByEmail("indore_relief@example.org")).thenReturn(Optional.empty());

        OrganisationAuthService.OrgRegistrationData data = new OrganisationAuthService.OrgRegistrationData(
                "Indore Relief NGO", "indore_relief@example.org", "Password123#",
                "India", "Madhya Pradesh", "Indore", "Palasia Square",
                null, null
        );

        orgAuthService.register(data);

        ArgumentCaptor<Organisation> captor = ArgumentCaptor.forClass(Organisation.class);
        verify(organisationRepository).save(captor.capture());
        Organisation saved = captor.getValue();

        assertEquals(22.7196, saved.getLatitude(), 0.05, "Org latitude must be near 22.7");
        assertEquals(75.8577, saved.getLongitude(), 0.05, "Org longitude must be near 75.9");
        assertNotNull(saved.getGeoLocation());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Authenticated Endpoint PUT /api/users/me/location
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Anonymous caller to PUT /api/users/me/location returns HTTP 401 Unauthorized")
    void testPutUserLocationRejectsAnonymous() throws Exception {
        Map<String, Object> body = Map.of("latitude", 22.7196, "longitude", 75.8577);

        mockMvc.perform(put("/api/users/me/location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("PUT /api/users/me/location rejects out-of-range latitude (> 90.0) with HTTP 400 Bad Request")
    void testPutUserLocationRejectsOutOfRangeLatitude() throws Exception {
        Map<String, Object> body = Map.of(
                "latitude", 125.0, // Invalid: exceeds 90.0
                "longitude", 75.8577
        );

        mockMvc.perform(put("/api/users/me/location")
                        .header("Authorization", "Bearer " + CITIZEN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.latitude", notNullValue()));
    }

    @Test
    @DisplayName("PUT /api/users/me/location rejects out-of-range longitude (> 180.0) with HTTP 400 Bad Request")
    void testPutUserLocationRejectsOutOfRangeLongitude() throws Exception {
        Map<String, Object> body = Map.of(
                "latitude", 22.7196,
                "longitude", 250.0 // Invalid: exceeds 180.0
        );

        mockMvc.perform(put("/api/users/me/location")
                        .header("Authorization", "Bearer " + CITIZEN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.longitude", notNullValue()));
    }

    @Test
    @DisplayName("PUT /api/users/me/location updates coordinates and syncs 2dsphere GeoLocation")
    void testPutUserLocationUpdatesCoordinatesSuccessfully() throws Exception {
        User existingUser = User.builder()
                .id("user-indore-101")
                .username(CITIZEN_USERNAME)
                .email("rahul@example.com")
                .role(UserRole.CITIZEN)
                .latitude(20.5937)
                .longitude(78.9629)
                .build();
        existingUser.syncGeo();

        when(userRepository.findByUsername(CITIZEN_USERNAME)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> updateBody = Map.of(
                "latitude", 22.7196,
                "longitude", 75.8577,
                "city", "Indore",
                "state", "Madhya Pradesh",
                "location", "Vijay Nagar"
        );

        mockMvc.perform(put("/api/users/me/location")
                        .header("Authorization", "Bearer " + CITIZEN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Location updated successfully"))
                .andExpect(jsonPath("$.latitude").value(22.7196))
                .andExpect(jsonPath("$.longitude").value(75.8577))
                .andExpect(jsonPath("$.city").value("Indore"))
                .andExpect(jsonPath("$.state").value("Madhya Pradesh"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User updated = captor.getValue();

        assertEquals(22.7196, updated.getLatitude());
        assertEquals(75.8577, updated.getLongitude());
        assertEquals("Indore", updated.getCity());
        assertNotNull(updated.getGeoLocation());
        assertEquals(75.8577, updated.getGeoLocation().getCoordinates()[0], 0.0001);
        assertEquals(22.7196, updated.getGeoLocation().getCoordinates()[1], 0.0001);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. One-time Migration Runner (LocationMigrationRunner)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("LocationMigrationRunner re-derives coordinates for users and orgs still at India-centre")
    void testLocationMigrationRunnerMigratesIndiaCentreEntities() {
        GeoLocationLookupService lookupService = new GeoLocationLookupService();

        User indoreUser = User.builder()
                .id("u1")
                .username("indore_resident")
                .city("Indore")
                .state("Madhya Pradesh")
                .latitude(20.5937) // Default India centre
                .longitude(78.9629)
                .build();

        User alreadyCorrectUser = User.builder()
                .id("u2")
                .username("bengaluru_resident")
                .city("Bengaluru")
                .state("Karnataka")
                .latitude(12.9716) // Already custom
                .longitude(77.5946)
                .build();

        Organisation indoreOrg = Organisation.builder()
                .id("org1")
                .organisationName("Indore Help Foundation")
                .city("Indore")
                .state("Madhya Pradesh")
                .latitude(20.5937) // Default India centre
                .longitude(78.9629)
                .build();

        when(userRepository.findAll()).thenReturn(List.of(indoreUser, alreadyCorrectUser));
        when(organisationRepository.findAll()).thenReturn(List.of(indoreOrg));

        LocationMigrationRunner runner = new LocationMigrationRunner(userRepository, organisationRepository, lookupService);
        runner.run();

        // Verify indoreUser was updated and saved
        verify(userRepository).save(indoreUser);
        assertEquals(22.7196, indoreUser.getLatitude(), 0.05);
        assertEquals(75.8577, indoreUser.getLongitude(), 0.05);
        assertNotNull(indoreUser.getGeoLocation());

        // Verify alreadyCorrectUser was NOT saved
        verify(userRepository, never()).save(alreadyCorrectUser);

        // Verify indoreOrg was updated and saved
        verify(organisationRepository).save(indoreOrg);
        assertEquals(22.7196, indoreOrg.getLatitude(), 0.05);
        assertEquals(75.8577, indoreOrg.getLongitude(), 0.05);
        assertNotNull(indoreOrg.getGeoLocation());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Dead branch removed from DisasterService
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DisasterService only targets users via geospatial radius and does not call findAll for 0.0/0.0 coords")
    void testDisasterServiceDoesNotCallFindAllForZeroCoordinates() {
        com.disaster.repository.DisasterEventRepository disasterRepo = mock(com.disaster.repository.DisasterEventRepository.class);
        com.disaster.repository.AlertRepository alertRepo = mock(com.disaster.repository.AlertRepository.class);
        com.disaster.repository.ShelterRepository shelterRepo = mock(com.disaster.repository.ShelterRepository.class);
        com.disaster.repository.VolunteerRepository volunteerRepo = mock(com.disaster.repository.VolunteerRepository.class);
        com.disaster.repository.UserRepository userRepoMock = mock(com.disaster.repository.UserRepository.class);
        com.disaster.repository.OrganisationRepository orgRepo = mock(com.disaster.repository.OrganisationRepository.class);
        com.disaster.service.EmailService emailSvc = mock(com.disaster.service.EmailService.class);
        ResourceMatchingService matchingService = mock(ResourceMatchingService.class);
        org.springframework.messaging.simp.SimpMessagingTemplate messaging = mock(org.springframework.messaging.simp.SimpMessagingTemplate.class);

        DisasterService disasterService = new DisasterService(
                disasterRepo, alertRepo, shelterRepo, volunteerRepo,
                userRepoMock, orgRepo, emailSvc, matchingService, messaging);

        com.disaster.model.DisasterEvent event = com.disaster.model.DisasterEvent.builder()
                .id("evt-101")
                .latitude(22.7196)
                .longitude(75.8577)
                .affectedRadius(25.0)
                .disasterType(com.disaster.model.DisasterType.FLOOD)
                .severity(8)
                .location("Indore, Madhya Pradesh")
                .build();

        when(disasterRepo.save(any())).thenReturn(event);
        when(shelterRepo.findSheltersWithinRadius(anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of());
        when(userRepoMock.findUsersWithinRadius(anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of());
        when(volunteerRepo.findAvailableVolunteersWithinRadius(anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of());
        when(matchingService.findNearestOrganisations(any(), anyInt())).thenReturn(List.of());
        when(matchingService.supportTypesFor(any())).thenReturn(List.of());

        disasterService.processDisaster(event);

        // Verification: userRepository.findUsersWithinRadius was called
        verify(userRepoMock).findUsersWithinRadius(eq(75.8577), eq(22.7196), eq(25000.0));
        // Verify userRepository.findAll() is NEVER called (the dead loop was removed)
        verify(userRepoMock, never()).findAll();
    }
}
