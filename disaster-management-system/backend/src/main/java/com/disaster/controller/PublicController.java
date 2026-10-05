package com.disaster.controller;

import com.disaster.dto.OrganisationCard;
import com.disaster.model.Alert;
import com.disaster.model.DisasterEvent;
import com.disaster.model.Organisation;
import com.disaster.model.Shelter;
import com.disaster.repository.AlertRepository;
import com.disaster.repository.DisasterEventRepository;
import com.disaster.repository.OrganisationRepository;
import com.disaster.repository.RescueRequestRepository;
import com.disaster.repository.ShelterRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/public")
public class PublicController {

    private final OrganisationRepository organisationRepository;
    private final ShelterRepository shelterRepository;
    private final AlertRepository alertRepository;
    private final RescueRequestRepository rescueRepository;
    private final DisasterEventRepository disasterEventRepository;

    public PublicController(
            OrganisationRepository organisationRepository,
            ShelterRepository shelterRepository,
            AlertRepository alertRepository,
            RescueRequestRepository rescueRepository,
            DisasterEventRepository disasterEventRepository) {
        this.organisationRepository = organisationRepository;
        this.shelterRepository = shelterRepository;
        this.alertRepository = alertRepository;
        this.rescueRepository = rescueRepository;
        this.disasterEventRepository = disasterEventRepository;
    }

    @GetMapping("/organisations")
    public ResponseEntity<List<OrganisationCard>> organisations() {
        List<OrganisationCard> cards = organisationRepository.findByVerifiedTrueAndActiveStatusTrue()
                .stream()
                .map(OrganisationCard::from)
                .toList();
        return ResponseEntity.ok(cards);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        long orgs = organisationRepository.findByVerifiedTrueAndActiveStatusTrue().size();
        long shelters = shelterRepository.findAll().size();
        long alerts = alertRepository.count();
        long rescues = rescueRepository.findByStatus(com.disaster.model.RescueRequest.RescueStatus.IN_PROGRESS).size();
        return ResponseEntity.ok(Map.of(
                "activeOrganisations", orgs,
                "sheltersAvailable", shelters,
                "totalAlerts", alerts,
                "activeRescueOperations", rescues,
                "mealsDistributed", shelters * 120
        ));
    }

    @GetMapping("/alerts")
    public ResponseEntity<List<Alert>> recentAlerts() {
        return ResponseEntity.ok(alertRepository.findTop20ByOrderByTimestampDesc());
    }

    @GetMapping("/events")
    public ResponseEntity<List<DisasterEvent>> recentEvents() {
        return ResponseEntity.ok(disasterEventRepository.findTop20ByOrderByTimestampDesc());
    }

    @GetMapping("/shelters")
    public ResponseEntity<List<com.disaster.dto.ShelterView>> publicShelters() {
        return ResponseEntity.ok(shelterRepository.findAll().stream().map(com.disaster.dto.ShelterView::from).toList());
    }
}
