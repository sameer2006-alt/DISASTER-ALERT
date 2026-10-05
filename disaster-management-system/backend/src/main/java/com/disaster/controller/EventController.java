package com.disaster.controller;

import com.disaster.dto.DisasterIngestDto;
import com.disaster.model.DisasterEvent;
import com.disaster.model.DisasterType;
import com.disaster.model.EventSource;
import com.disaster.repository.DisasterEventRepository;
import com.disaster.service.EventProcessorService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventProcessorService eventProcessor;
    private final DisasterEventRepository disasterRepository;

    public EventController(EventProcessorService eventProcessor, DisasterEventRepository disasterRepository) {
        this.eventProcessor = eventProcessor;
        this.disasterRepository = disasterRepository;
    }

    @GetMapping("/active")
    public ResponseEntity<List<DisasterEvent>> active() {
        return ResponseEntity.ok(disasterRepository.findTop20ByOrderByTimestampDesc());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DisasterEvent> getById(@PathVariable String id) {
        return disasterRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/simulate")
    public ResponseEntity<DisasterEvent> simulate(@RequestBody SimulateRequest req) {
        DisasterType type = req.type() != null ? req.type() : DisasterType.FLOOD;
        DisasterEvent event = DisasterEvent.builder()
                .disasterType(type)
                .severity(req.severity() > 0 ? req.severity() : 8)
                .location(req.location() != null ? req.location() : "Simulated Zone, India")
                .latitude(req.latitude() != 0 ? req.latitude() : 19.0760)
                .longitude(req.longitude() != 0 ? req.longitude() : 72.8777)
                .timestamp(Instant.now())
                .message("SIMULATION: " + type + " detected in affected region. Take immediate precautions.")
                .affectedRadius(req.affectedRadius() > 0 ? req.affectedRadius() : 25)
                .source(EventSource.SIMULATION)
                .build();
        return ResponseEntity.ok(eventProcessor.processEvent(event));
    }

    @PostMapping("/ingest")
    public ResponseEntity<DisasterEvent> ingest(@Valid @RequestBody DisasterIngestDto dto) {
        DisasterEvent event = dto.toEntity();
        return ResponseEntity.ok(eventProcessor.processEvent(event));
    }


    public record SimulateRequest(
            DisasterType type, int severity,
            String location, double latitude, double longitude, double affectedRadius) {}
}
