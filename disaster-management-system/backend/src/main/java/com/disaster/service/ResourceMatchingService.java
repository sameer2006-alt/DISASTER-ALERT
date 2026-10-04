package com.disaster.service;

import com.disaster.model.DisasterEvent;
import com.disaster.model.DisasterType;
import com.disaster.model.Organisation;
import com.disaster.repository.OrganisationRepository;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.NearQuery;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.GeoResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ResourceMatchingService {

    private final OrganisationRepository organisationRepository;
    private final MongoTemplate mongoTemplate;

    public ResourceMatchingService(OrganisationRepository organisationRepository, MongoTemplate mongoTemplate) {
        this.organisationRepository = organisationRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public List<Organisation> findNearestOrganisations(DisasterEvent event, int limit) {
        if (organisationRepository.count() == 0) {
            return List.of();
        }

        List<String> requiredTypes = supportTypesFor(event.getDisasterType());
        Point point = new Point(event.getLongitude(), event.getLatitude());
        double radiusKm = Math.max(event.getAffectedRadius(), 50);
        NearQuery nearQuery = NearQuery.near(point)
                .maxDistance(new Distance(radiusKm, Metrics.KILOMETERS))
                .query(Query.query(Criteria.where("activeStatus").is(true).and("verified").is(true)));

        List<Organisation> nearby;
        try {
            GeoResults<Organisation> geoResults = mongoTemplate.geoNear(nearQuery, Organisation.class);
            if (geoResults == null || geoResults.getContent().isEmpty()) {
                return List.of();
            }
            nearby = geoResults.getContent().stream()
                    .map(GeoResult::getContent)
                    .toList();
        } catch (Exception e) {
            return List.of();
        }

        return nearby.stream()
                .filter(o -> o.getSupportTypes() != null && o.getSupportTypes().stream().anyMatch(requiredTypes::contains))
                .sorted(Comparator.comparingInt((Organisation o) -> score(o, event)).reversed())
                .limit(limit)
                .toList();
    }

    private int score(Organisation org, DisasterEvent event) {
        int score = event.getSeverity();
        if ("GOVERNMENT".equals(org.getVerificationBadge())) score += 5;
        if ("VERIFIED".equals(org.getVerificationBadge())) score += 3;
        return score;
    }

    public List<String> supportTypesFor(DisasterType type) {
        return switch (type) {
            case FLOOD -> List.of("Rescue Boats", "Drinking Water", "Shelter", "Food");
            case FIRE -> List.of("Medical Aid", "Shelter", "Ambulance", "Evacuation Transport");
            case EARTHQUAKE -> List.of("Medical Aid", "Shelter", "Food", "Rescue Equipment");
            case CYCLONE -> List.of("Shelter", "Food", "Drinking Water", "Evacuation Transport");
            case LANDSLIDE -> List.of("Rescue Team", "Medical Aid", "Shelter");
        };
    }
}
