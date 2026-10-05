package com.disaster.service;

import com.disaster.dto.AlertMessage;
import com.disaster.dto.OrgPublicView;
import com.disaster.dto.ShelterView;
import com.disaster.dto.VolunteerView;
import com.disaster.model.*;
import com.disaster.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class DisasterService {

    private static final Logger log = LoggerFactory.getLogger(DisasterService.class);

    private final DisasterEventRepository disasterRepository;
    private final AlertRepository alertRepository;
    private final ShelterRepository shelterRepository;
    private final VolunteerRepository volunteerRepository;
    private final UserRepository userRepository;
    private final OrganisationRepository organisationRepository;
    private final EmailService emailService;
    private final ResourceMatchingService matchingService;
    private final SimpMessagingTemplate messagingTemplate;

    public DisasterService(
            DisasterEventRepository disasterRepository,
            AlertRepository alertRepository,
            ShelterRepository shelterRepository,
            VolunteerRepository volunteerRepository,
            UserRepository userRepository,
            OrganisationRepository organisationRepository,
            EmailService emailService,
            ResourceMatchingService matchingService,
            SimpMessagingTemplate messagingTemplate) {
        this.disasterRepository = disasterRepository;
        this.alertRepository = alertRepository;
        this.shelterRepository = shelterRepository;
        this.volunteerRepository = volunteerRepository;
        this.userRepository = userRepository;
        this.organisationRepository = organisationRepository;
        this.emailService = emailService;
        this.matchingService = matchingService;
        this.messagingTemplate = messagingTemplate;
    }

    public SimulationResult processDisaster(DisasterEvent event) {
        if (event.getTimestamp() == null) {
            event.setTimestamp(Instant.now());
        }
        event.setActive(true);
        event.syncGeo();
        DisasterEvent savedEvent = disasterRepository.save(event);
        log.info("Simulation activated disaster: {} with ID: {}", savedEvent.getDisasterType(), savedEvent.getId());

        // Save alert log
        Alert alert = Alert.builder()
                .disasterEventId(savedEvent.getId())
                .disasterType(savedEvent.getDisasterType())
                .severity(savedEvent.getSeverity())
                .message(savedEvent.getMessage())
                .location(savedEvent.getLocation())
                .latitude(savedEvent.getLatitude())
                .longitude(savedEvent.getLongitude())
                .timestamp(savedEvent.getTimestamp())
                .build();
        alertRepository.save(alert);

        // Broadcast WebSockets
        AlertMessage wsMessage = AlertMessage.from(savedEvent);
        messagingTemplate.convertAndSend("/topic/alerts", wsMessage);
        messagingTemplate.convertAndSend("/topic/disaster-alerts", wsMessage);
        messagingTemplate.convertAndSend("/topic/alert-stream", wsMessage);
        messagingTemplate.convertAndSend("/topic/dashboard", wsMessage);

        double radiusInMeters = savedEvent.getAffectedRadius() * 1000.0;

        // 1. Activate shelters in the area
        List<Shelter> nearbyShelters = shelterRepository.findSheltersWithinRadius(
                savedEvent.getLongitude(), savedEvent.getLatitude(), radiusInMeters);
        int sheltersActivated = 0;
        for (Shelter shelter : nearbyShelters) {
            if (shelter.getStatus() != Shelter.ShelterStatus.ACTIVE) {
                shelter.setStatus(Shelter.ShelterStatus.ACTIVE);
                shelterRepository.save(shelter);
                sheltersActivated++;
            }
            messagingTemplate.convertAndSend("/topic/shelters", ShelterView.from(shelter));
        }

        // Get shelter fallback string
        String nearestShelterName = nearbyShelters.isEmpty() ? "Check platform maps for safe shelters" : nearbyShelters.get(0).getName();

        // 2. Identify citizens in the area and send email alerts
        List<User> nearbyUsers = new java.util.ArrayList<>(userRepository.findUsersWithinRadius(
                savedEvent.getLongitude(), savedEvent.getLatitude(), radiusInMeters));
        
        // Add fallback users matching state/city if they don't have geo coordinates set
        String eventLocation = savedEvent.getLocation() != null ? savedEvent.getLocation().toLowerCase() : "";
        List<User> allUsers = userRepository.findAll();
        for (User user : allUsers) {
            // Check if user is not already in nearbyUsers to avoid duplicates
            if (nearbyUsers.stream().noneMatch(u -> u.getId().equals(user.getId()))) {
                // Check if user has no valid coordinates (latitude == 0.0 && longitude == 0.0)
                if (user.getLatitude() == 0.0 && user.getLongitude() == 0.0) {
                    boolean cityMatch = user.getCity() != null && !user.getCity().isEmpty() 
                            && eventLocation.contains(user.getCity().toLowerCase());
                    boolean stateMatch = user.getState() != null && !user.getState().isEmpty() 
                            && eventLocation.contains(user.getState().toLowerCase());
                    if (cityMatch || stateMatch) {
                        nearbyUsers.add(user);
                    }
                }
            }
        }

        int affectedUserCount = 0;
        int emailsSent = 0;
        for (User user : nearbyUsers) {
            if (user.getRole() == UserRole.CITIZEN) {
                affectedUserCount++;
                if (user.isVerified()) {
                    emailService.sendDisasterAlert(user.getEmail(), savedEvent, nearestShelterName);
                    emailsSent++;
                }
            }
        }

        // 3. Dispatch nearest volunteers
        List<Volunteer> nearbyVolunteers = volunteerRepository.findAvailableVolunteersWithinRadius(
                savedEvent.getLongitude(), savedEvent.getLatitude(), radiusInMeters);
        for (Volunteer volunteer : nearbyVolunteers) {
            volunteer.setStatus(Volunteer.VolunteerStatus.ON_MISSION);
            volunteer.setAssignedDisasterId(savedEvent.getId());
            volunteerRepository.save(volunteer);
            VolunteerView vView = VolunteerView.from(volunteer);
            messagingTemplate.convertAndSend("/topic/volunteers", vView);
            messagingTemplate.convertAndSend("/topic/rescue", vView);
        }

        // 4. Match and notify organisations
        List<Organisation> organisations = matchingService.findNearestOrganisations(savedEvent, 5);
        List<String> requiredResources = matchingService.supportTypesFor(savedEvent.getDisasterType());
        for (Organisation org : organisations) {
            emailService.sendOrgDisasterAlert(org.getEmail(), savedEvent, requiredResources);
            OrgPublicView orgView = OrgPublicView.from(org);
            messagingTemplate.convertAndSend("/topic/org-status", orgView);
            messagingTemplate.convertAndSend("/topic/rescue", orgView);

            // targeted org WS notification
            java.util.Map<String, Object> orgNotification = java.util.Map.of(
                "disasterId", savedEvent.getId(),
                "disasterType", savedEvent.getDisasterType().name(),
                "severity", savedEvent.getSeverity(),
                "location", savedEvent.getLocation(),
                "requiredResources", requiredResources
            );
            messagingTemplate.convertAndSend("/topic/org/" + org.getId(), orgNotification);
        }

        return new SimulationResult(savedEvent.getId(), affectedUserCount, emailsSent, sheltersActivated, savedEvent);
    }

    public void resolveDisaster(String id) {
        DisasterEvent event = disasterRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Disaster event not found with ID: " + id));

        event.setActive(false);
        disasterRepository.save(event);
        log.info("Disaster event resolved: {}", id);

        double radiusInMeters = event.getAffectedRadius() * 1000.0;

        // Reset shelters in area
        List<Shelter> shelters = shelterRepository.findSheltersWithinRadius(
                event.getLongitude(), event.getLatitude(), radiusInMeters);
        for (Shelter shelter : shelters) {
            shelter.setStatus(Shelter.ShelterStatus.INACTIVE);
            shelterRepository.save(shelter);
            messagingTemplate.convertAndSend("/topic/shelters", ShelterView.from(shelter));
        }

        // Reset volunteers assigned to this event
        List<Volunteer> volunteers = volunteerRepository.findAll().stream()
                .filter(v -> id.equals(v.getAssignedDisasterId()))
                .toList();
        for (Volunteer volunteer : volunteers) {
            volunteer.setStatus(Volunteer.VolunteerStatus.AVAILABLE);
            volunteer.setAssignedDisasterId(null);
            volunteerRepository.save(volunteer);
            VolunteerView vView = VolunteerView.from(volunteer);
            messagingTemplate.convertAndSend("/topic/volunteers", vView);
            messagingTemplate.convertAndSend("/topic/rescue", vView);
        }

        // Broadcast resolution alert
        AlertMessage wsMessage = AlertMessage.from(event);
        messagingTemplate.convertAndSend("/topic/alerts", wsMessage);
        messagingTemplate.convertAndSend("/topic/disaster-alerts", wsMessage);
        messagingTemplate.convertAndSend("/topic/alert-stream", wsMessage);
        messagingTemplate.convertAndSend("/topic/dashboard", wsMessage);
    }

    public record SimulationResult(
            String id,
            int affectedUserCount,
            int emailsSent,
            int sheltersActivated,
            DisasterEvent event
    ) {}
}
