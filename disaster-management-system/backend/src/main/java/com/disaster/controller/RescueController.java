package com.disaster.controller;

import com.disaster.dto.RescueView;
import com.disaster.model.RescueRequest;
import com.disaster.repository.RescueRequestRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/rescue")
public class RescueController {

    private final RescueRequestRepository rescueRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final com.disaster.repository.OrganisationRepository organisationRepository;
    private final com.disaster.service.EmailService emailService;
    private final org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;

    public RescueController(
            RescueRequestRepository rescueRepository,
            SimpMessagingTemplate messagingTemplate,
            com.disaster.repository.OrganisationRepository organisationRepository,
            com.disaster.service.EmailService emailService,
            org.springframework.data.mongodb.core.MongoTemplate mongoTemplate) {
        this.rescueRepository = rescueRepository;
        this.messagingTemplate = messagingTemplate;
        this.organisationRepository = organisationRepository;
        this.emailService = emailService;
        this.mongoTemplate = mongoTemplate;
    }

    @PostMapping("/request")
    public ResponseEntity<RescueView> create(@RequestBody RescueRequest request) {
        if (request.getLatitude() == 0.0 || request.getLongitude() == 0.0) {
            throw new IllegalArgumentException("Location required for SOS");
        }
        request.setStatus(RescueRequest.RescueStatus.PENDING);
        request.setCreatedAt(Instant.now());
        request.syncGeo();
        RescueRequest saved = rescueRepository.save(request);
        RescueView view = RescueView.from(saved);
        messagingTemplate.convertAndSend("/topic/rescue", view);
        messagingTemplate.convertAndSend("/topic/rescue-requests", view);

        // Notify organisations near the SOS beacon's coordinates (within 50km)
        org.springframework.data.geo.Point point = new org.springframework.data.geo.Point(saved.getLongitude(), saved.getLatitude());
        org.springframework.data.mongodb.core.query.NearQuery nearQuery = org.springframework.data.mongodb.core.query.NearQuery.near(point)
                .maxDistance(new org.springframework.data.geo.Distance(5000, org.springframework.data.geo.Metrics.KILOMETERS))
                .query(org.springframework.data.mongodb.core.query.Query.query(
                        org.springframework.data.mongodb.core.query.Criteria.where("activeStatus").is(true).and("verified").is(true)
                ));

        List<com.disaster.model.Organisation> nearbyOrganisations = new java.util.ArrayList<>();
        try {
            org.springframework.data.geo.GeoResults<com.disaster.model.Organisation> geoResults = 
                    mongoTemplate.geoNear(nearQuery, com.disaster.model.Organisation.class);
            for (org.springframework.data.geo.GeoResult<com.disaster.model.Organisation> res : geoResults.getContent()) {
                nearbyOrganisations.add(res.getContent());
            }
        } catch (Exception e) {
            System.err.println("SOS nearby organisation geospatial lookup failed: " + e.getMessage());
        }

        // Fallback: If no organisations are within 50km, notify all active/verified organisations
        if (nearbyOrganisations.isEmpty()) {
            nearbyOrganisations = organisationRepository.findByVerifiedTrueAndActiveStatusTrue();
        }

        for (com.disaster.model.Organisation org : nearbyOrganisations) {
            emailService.sendSosAlertToOrganisation(org.getEmail(), saved);
        }

        // Send SOS alert to central authority if configured
        String authorityEmail = emailService.getSosAuthorityEmail();
        if (authorityEmail != null && !authorityEmail.isBlank()) {
            emailService.sendSosAlertToAuthority(authorityEmail, saved);
        }

        return ResponseEntity.ok(view);
    }

    @GetMapping
    public ResponseEntity<List<RescueView>> list() {
        return ResponseEntity.ok(rescueRepository.findAll().stream().map(RescueView::from).toList());
    }

    @GetMapping("/pending")
    public ResponseEntity<List<RescueView>> pending() {
        return ResponseEntity.ok(rescueRepository.findByStatus(RescueRequest.RescueStatus.PENDING).stream().map(RescueView::from).toList());
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<RescueView> updateStatus(
            @PathVariable String id,
            @RequestBody StatusUpdate update) {
        RescueRequest req = rescueRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));
        req.setStatus(update.status());
        RescueRequest saved = rescueRepository.save(req);
        RescueView view = RescueView.from(saved);
        messagingTemplate.convertAndSend("/topic/rescue", view);
        return ResponseEntity.ok(view);
    }

    public record StatusUpdate(RescueRequest.RescueStatus status) {}
}
