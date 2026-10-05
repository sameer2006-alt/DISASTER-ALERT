package com.disaster.controller;

import com.disaster.dto.ShelterView;
import com.disaster.model.Shelter;
import com.disaster.repository.ShelterRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shelters")
public class ShelterController {

    private final ShelterRepository shelterRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public ShelterController(ShelterRepository shelterRepository, SimpMessagingTemplate messagingTemplate) {
        this.shelterRepository = shelterRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @GetMapping
    public ResponseEntity<List<ShelterView>> list() {
        return ResponseEntity.ok(shelterRepository.findAll().stream().map(ShelterView::from).toList());
    }

    @PostMapping
    public ResponseEntity<ShelterView> create(@RequestBody Shelter shelter) {
        shelter.syncGeo();
        if (shelter.getStatus() == null) shelter.setStatus(Shelter.ShelterStatus.INACTIVE);
        Shelter saved = shelterRepository.save(shelter);
        ShelterView view = ShelterView.from(saved);
        messagingTemplate.convertAndSend("/topic/shelters", view);
        return ResponseEntity.ok(view);
    }

    @PatchMapping("/{id}/occupancy")
    public ResponseEntity<ShelterView> updateOccupancy(@PathVariable String id, @RequestBody OccupancyUpdate update) {
        Shelter shelter = shelterRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Shelter not found"));
        shelter.setAvailableBeds(update.availableBeds());
        if (shelter.getAvailableBeds() <= 0) {
            shelter.setStatus(Shelter.ShelterStatus.FULL);
        }
        Shelter saved = shelterRepository.save(shelter);
        ShelterView view = ShelterView.from(saved);
        messagingTemplate.convertAndSend("/topic/shelters", view);
        return ResponseEntity.ok(view);
    }

    public record OccupancyUpdate(int availableBeds) {}
}
