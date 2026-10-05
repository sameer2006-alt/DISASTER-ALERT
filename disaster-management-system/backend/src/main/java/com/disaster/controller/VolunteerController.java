package com.disaster.controller;

import com.disaster.dto.VolunteerOrgView;
import com.disaster.dto.VolunteerView;
import com.disaster.model.Volunteer;
import com.disaster.repository.VolunteerRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/volunteers")
public class VolunteerController {

    private final VolunteerRepository volunteerRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public VolunteerController(VolunteerRepository volunteerRepository, SimpMessagingTemplate messagingTemplate) {
        this.volunteerRepository = volunteerRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @GetMapping
    public ResponseEntity<List<VolunteerOrgView>> list() {
        return ResponseEntity.ok(volunteerRepository.findAll().stream().map(VolunteerOrgView::from).toList());
    }

    @PostMapping
    public ResponseEntity<VolunteerOrgView> create(@RequestBody Volunteer volunteer) {
        volunteer.syncGeo();
        if (volunteer.getStatus() == null) {
            volunteer.setStatus(Volunteer.VolunteerStatus.AVAILABLE);
        }
        Volunteer saved = volunteerRepository.save(volunteer);
        VolunteerView publicView = VolunteerView.from(saved);
        try {
            messagingTemplate.convertAndSend("/topic/volunteers", publicView);
        } catch (Exception ignored) {}
        return ResponseEntity.ok(VolunteerOrgView.from(saved));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<VolunteerOrgView> updateStatus(@PathVariable String id, @RequestBody StatusUpdate update) {
        Volunteer volunteer = volunteerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Volunteer not found"));
        if (update.status() != null) {
            volunteer.setStatus(update.status());
        }
        Volunteer saved = volunteerRepository.save(volunteer);
        VolunteerView publicView = VolunteerView.from(saved);
        try {
            messagingTemplate.convertAndSend("/topic/volunteers", publicView);
        } catch (Exception ignored) {}
        return ResponseEntity.ok(VolunteerOrgView.from(saved));
    }

    public record StatusUpdate(Volunteer.VolunteerStatus status) {}
}

