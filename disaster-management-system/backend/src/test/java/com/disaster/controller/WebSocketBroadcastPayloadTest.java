package com.disaster.controller;

import com.disaster.dto.RescueView;
import com.disaster.dto.ShelterView;
import com.disaster.dto.VolunteerOrgView;
import com.disaster.dto.VolunteerView;
import com.disaster.model.GeoLocation;
import com.disaster.model.RescueRequest;
import com.disaster.model.Shelter;
import com.disaster.model.Volunteer;
import com.disaster.repository.OrganisationRepository;
import com.disaster.repository.RescueRequestRepository;
import com.disaster.repository.ShelterRepository;
import com.disaster.repository.VolunteerRepository;
import com.disaster.service.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WebSocketBroadcastPayloadTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private VolunteerRepository volunteerRepository;

    @Mock
    private ShelterRepository shelterRepository;

    @Mock
    private RescueRequestRepository rescueRepository;

    @Mock
    private OrganisationRepository organisationRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private MongoTemplate mongoTemplate;

    private VolunteerController volunteerController;
    private ShelterController shelterController;
    private RescueController rescueController;

    @BeforeEach
    void setUp() {
        volunteerController = new VolunteerController(volunteerRepository, messagingTemplate);
        shelterController = new ShelterController(shelterRepository, messagingTemplate);
        rescueController = new RescueController(
                rescueRepository, messagingTemplate, organisationRepository, emailService, mongoTemplate
        );
    }

    @Test
    @DisplayName("VolunteerController.create() returns VolunteerOrgView (with phone) but broadcasts VolunteerView (without phone)")
    void testVolunteerCreateBroadcastPayload() throws Exception {
        Volunteer input = Volunteer.builder()
                .id("vol-123")
                .userId("internal-user-abc")
                .name("Alex Rivers")
                .contact("+919876543210")
                .role("Medical Specialist")
                .skills(List.of("Triage", "Paramedic"))
                .status(Volunteer.VolunteerStatus.AVAILABLE)
                .assignedDisasterId("disaster-1")
                .latitude(19.0760)
                .longitude(72.8777)
                .build();

        when(volunteerRepository.save(any(Volunteer.class))).thenReturn(input);

        ResponseEntity<VolunteerOrgView> response = volunteerController.create(input);

        // 1. Authenticated REST response must be VolunteerOrgView and contain contact phone number
        assertNotNull(response.getBody());
        VolunteerOrgView orgView = response.getBody();
        assertEquals("vol-123", orgView.id());
        assertEquals("Alex Rivers", orgView.name());
        assertEquals("+919876543210", orgView.contact(), "VolunteerOrgView MUST contain contact phone number");
        assertFalse(objectMapper.writeValueAsString(orgView).contains("internal-user-abc"), "REST response must omit internal userId");

        // 2. WebSocket broadcast must be VolunteerView and NOT contain contact phone number
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/volunteers"), payloadCaptor.capture());

        Object broadcastObj = payloadCaptor.getValue();
        assertInstanceOf(VolunteerView.class, broadcastObj, "WebSocket broadcast must be VolunteerView, not full entity or VolunteerOrgView");

        VolunteerView publicBroadcast = (VolunteerView) broadcastObj;
        assertEquals("vol-123", publicBroadcast.id());
        assertEquals("Alex Rivers", publicBroadcast.name());

        String broadcastJson = objectMapper.writeValueAsString(publicBroadcast);
        assertFalse(broadcastJson.contains("+919876543210"), "WebSocket broadcast JSON must NOT leak volunteer phone number");
        assertFalse(broadcastJson.contains("internal-user-abc"), "WebSocket broadcast JSON must NOT leak internal userId");
    }

    @Test
    @DisplayName("VolunteerController.updateStatus() returns VolunteerOrgView (with phone) but broadcasts VolunteerView (without phone)")
    void testVolunteerUpdateStatusBroadcastPayload() throws Exception {
        Volunteer existing = Volunteer.builder()
                .id("vol-456")
                .userId("internal-user-xyz")
                .name("Samira Khan")
                .contact("+919811122233")
                .role("Search and Rescue")
                .skills(List.of("K9 Handler"))
                .status(Volunteer.VolunteerStatus.AVAILABLE)
                .latitude(28.6139)
                .longitude(77.2090)
                .build();

        when(volunteerRepository.findById("vol-456")).thenReturn(Optional.of(existing));
        when(volunteerRepository.save(any(Volunteer.class))).thenReturn(existing);

        VolunteerController.StatusUpdate update = new VolunteerController.StatusUpdate(Volunteer.VolunteerStatus.ON_MISSION);
        ResponseEntity<VolunteerOrgView> response = volunteerController.updateStatus("vol-456", update);

        // 1. Authenticated REST response must contain contact phone
        assertNotNull(response.getBody());
        assertEquals("+919811122233", response.getBody().contact());

        // 2. WebSocket broadcast must be VolunteerView and omit contact
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/volunteers"), payloadCaptor.capture());

        assertInstanceOf(VolunteerView.class, payloadCaptor.getValue());
        VolunteerView broadcast = (VolunteerView) payloadCaptor.getValue();
        String broadcastJson = objectMapper.writeValueAsString(broadcast);
        assertFalse(broadcastJson.contains("+919811122233"), "WebSocket broadcast JSON must NOT leak phone");
        assertFalse(broadcastJson.contains("internal-user-xyz"), "WebSocket broadcast JSON must NOT leak userId");
    }

    @Test
    @DisplayName("ShelterController.create() broadcasts ShelterView without internal passwords or sensitive data")
    void testShelterBroadcastPayload() throws Exception {
        Shelter shelter = Shelter.builder()
                .id("shelter-10")
                .name("Community Shelter East")
                .organisationId("org-50")
                .capacity(200)
                .availableBeds(80)
                .foodAvailable(true)
                .medicalAvailable(true)
                .latitude(19.076)
                .longitude(72.8777)
                .contactDetails("022-1234567")
                .status(Shelter.ShelterStatus.ACTIVE)
                .build();

        when(shelterRepository.save(any(Shelter.class))).thenReturn(shelter);

        ResponseEntity<ShelterView> response = shelterController.create(shelter);
        assertNotNull(response.getBody());
        assertInstanceOf(ShelterView.class, response.getBody());

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/shelters"), captor.capture());

        assertInstanceOf(ShelterView.class, captor.getValue());
        String json = objectMapper.writeValueAsString(captor.getValue());
        assertTrue(json.contains("Community Shelter East"));
        assertFalse(json.contains("password"));
    }

    @Test
    @DisplayName("RescueController.create() broadcasts RescueView without user ID")
    void testRescueBroadcastPayload() throws Exception {
        RescueRequest req = RescueRequest.builder()
                .id("rescue-100")
                .userId("sensitive-citizen-user-id")
                .description("Stranded by rising flood waters")
                .priority("HIGH")
                .latitude(19.076)
                .longitude(72.877)
                .status(RescueRequest.RescueStatus.PENDING)
                .build();

        when(rescueRepository.save(any(RescueRequest.class))).thenReturn(req);

        ResponseEntity<RescueView> response = rescueController.create(req);
        assertNotNull(response.getBody());
        assertInstanceOf(RescueView.class, response.getBody());

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/rescue"), captor.capture());

        assertInstanceOf(RescueView.class, captor.getValue());
        String json = objectMapper.writeValueAsString(captor.getValue());
        assertFalse(json.contains("sensitive-citizen-user-id"), "Rescue broadcast must NOT leak citizen user ID");
    }
}
