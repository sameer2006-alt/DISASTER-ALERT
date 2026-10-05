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
public class EventProcessorService {

    private static final Logger log = LoggerFactory.getLogger(EventProcessorService.class);

    private final DisasterEventRepository disasterRepository;
    private final AlertRepository alertRepository;
    private final ShelterRepository shelterRepository;
    private final VolunteerRepository volunteerRepository;
    private final UserRepository userRepository;
    private final OrganisationRepository organisationRepository;
    private final EmailService emailService;
    private final ResourceMatchingService matchingService;
    private final SimpMessagingTemplate messagingTemplate;

    public EventProcessorService(
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

    public DisasterEvent processEvent(DisasterEvent event) {
        if (event.getTimestamp() == null) {
            event.setTimestamp(Instant.now());
        }
        event.syncGeo();
        DisasterEvent saved = disasterRepository.save(event);
        log.info("Processed disaster event: {} {}", saved.getDisasterType(), saved.getId());

        Alert alert = Alert.builder()
                .disasterEventId(saved.getId())
                .disasterType(saved.getDisasterType())
                .severity(saved.getSeverity())
                .message(saved.getMessage())
                .location(saved.getLocation())
                .latitude(saved.getLatitude())
                .longitude(saved.getLongitude())
                .timestamp(saved.getTimestamp())
                .build();
        alertRepository.save(alert);

        AlertMessage wsMessage = AlertMessage.builder()
                .id(saved.getId())
                .disasterType(saved.getDisasterType())
                .severity(saved.getSeverity())
                .location(saved.getLocation())
                .latitude(saved.getLatitude())
                .longitude(saved.getLongitude())
                .message(saved.getMessage())
                .timestamp(saved.getTimestamp())
                .affectedRadius(saved.getAffectedRadius())
                .source(saved.getSource())
                .state(saved.getState())
                .officialSeverity(saved.getOfficialSeverity())
                .sourceUrl(saved.getSourceUrl())
                .build();
        messagingTemplate.convertAndSend("/topic/alerts", wsMessage);
        messagingTemplate.convertAndSend("/topic/dashboard", wsMessage);


        activateShelters(saved);
        assignVolunteers(saved);
        notifyUsers(saved);
        notifyOrganisations(saved);

        return saved;
    }

    private void activateShelters(DisasterEvent event) {
        shelterRepository.findAll().stream()
                .filter(s -> distanceKm(s.getLatitude(), s.getLongitude(), event.getLatitude(), event.getLongitude())
                        <= event.getAffectedRadius())
                .forEach(s -> {
                    s.setStatus(Shelter.ShelterStatus.ACTIVE);
                    shelterRepository.save(s);
                    messagingTemplate.convertAndSend("/topic/shelters", ShelterView.from(s));
                });
    }

    private void assignVolunteers(DisasterEvent event) {
        volunteerRepository.findByStatus(Volunteer.VolunteerStatus.AVAILABLE).stream()
                .limit(5)
                .forEach(v -> {
                    v.setStatus(Volunteer.VolunteerStatus.ON_MISSION);
                    v.setAssignedDisasterId(event.getId());
                    volunteerRepository.save(v);
                    messagingTemplate.convertAndSend("/topic/volunteers", VolunteerView.from(v));
                });
    }

    private void notifyUsers(DisasterEvent event) {
        String shelterName = shelterRepository.findAll().stream()
                .filter(s -> s.getStatus() == Shelter.ShelterStatus.ACTIVE)
                .map(Shelter::getName)
                .findFirst()
                .orElse("See dashboard for shelters");

        userRepository.findAll().stream()
                .filter(User::isVerified)
                .limit(50)
                .forEach(u -> emailService.sendDisasterAlert(u.getEmail(), event, shelterName));
    }

    private void notifyOrganisations(DisasterEvent event) {
        List<Organisation> matched = matchingService.findNearestOrganisations(event, 10);
        matched.forEach(org -> {
            emailService.sendDisasterAlert(org.getEmail(), event, "Your team has been matched to this zone");
            messagingTemplate.convertAndSend("/topic/org-status", OrgPublicView.from(org));
        });
    }

    private double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
