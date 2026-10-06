package com.disaster.config;

import com.disaster.model.Organisation;
import com.disaster.model.User;
import com.disaster.repository.OrganisationRepository;
import com.disaster.repository.UserRepository;
import com.disaster.service.GeoLocationLookupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One-time data migration runner to fix existing users and organisations that were
 * saved at default India-centre coordinates (20.5937, 78.9629) by re-deriving their
 * coordinates from their registered state and city.
 *
 * Enabled ONLY when the Spring profile "migrate" is active:
 *   e.g. java -Dspring.profiles.active=migrate,dev -jar app.jar
 */
@Component
@Profile("migrate")
public class LocationMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(LocationMigrationRunner.class);

    private final UserRepository userRepository;
    private final OrganisationRepository organisationRepository;
    private final GeoLocationLookupService geoLocationLookupService;

    public LocationMigrationRunner(
            UserRepository userRepository,
            OrganisationRepository organisationRepository,
            GeoLocationLookupService geoLocationLookupService) {
        this.userRepository = userRepository;
        this.organisationRepository = organisationRepository;
        this.geoLocationLookupService = geoLocationLookupService;
    }

    @Override
    public void run(String... args) {
        log.info("===============================================================================");
        log.info("STARTING LOCATION MIGRATION: Re-deriving coordinates for India-centre entities...");
        log.info("===============================================================================");

        int migratedUsers = 0;
        int unchangedUsers = 0;
        List<User> users = userRepository.findAll();
        for (User user : users) {
            if (isAtCentreOrUnset(user.getLatitude(), user.getLongitude())) {
                var coordsOpt = geoLocationLookupService.lookup(user.getCity(), user.getState());
                if (coordsOpt.isPresent()) {
                    var coords = coordsOpt.get();
                    user.setLatitude(coords.latitude());
                    user.setLongitude(coords.longitude());
                    user.syncGeo();
                    userRepository.save(user);
                    log.info("Migrated user [id={}, username={}, city={}, state={}] -> lat={}, lon={} (approx={})",
                            user.getId(), user.getUsername(), user.getCity(), user.getState(),
                            coords.latitude(), coords.longitude(), coords.approximate());
                    migratedUsers++;
                } else {
                    log.warn("Could not resolve coordinates for user [id={}, username={}, city='{}', state='{}']",
                            user.getId(), user.getUsername(), user.getCity(), user.getState());
                    unchangedUsers++;
                }
            } else {
                unchangedUsers++;
            }
        }

        int migratedOrgs = 0;
        int unchangedOrgs = 0;
        List<Organisation> organisations = organisationRepository.findAll();
        for (Organisation org : organisations) {
            if (isAtCentreOrUnset(org.getLatitude(), org.getLongitude())) {
                var coordsOpt = geoLocationLookupService.lookup(org.getCity(), org.getState());
                if (coordsOpt.isPresent()) {
                    var coords = coordsOpt.get();
                    org.setLatitude(coords.latitude());
                    org.setLongitude(coords.longitude());
                    org.syncGeo();
                    organisationRepository.save(org);
                    log.info("Migrated organisation [id={}, name={}, city={}, state={}] -> lat={}, lon={} (approx={})",
                            org.getId(), org.getOrganisationName(), org.getCity(), org.getState(),
                            coords.latitude(), coords.longitude(), coords.approximate());
                    migratedOrgs++;
                } else {
                    log.warn("Could not resolve coordinates for organisation [id={}, name={}, city='{}', state='{}']",
                            org.getId(), org.getOrganisationName(), org.getCity(), org.getState());
                    unchangedOrgs++;
                }
            } else {
                unchangedOrgs++;
            }
        }

        log.info("===============================================================================");
        log.info("LOCATION MIGRATION COMPLETED: Migrated {} users ({} unchanged), {} organisations ({} unchanged).",
                migratedUsers, unchangedUsers, migratedOrgs, unchangedOrgs);
        log.info("===============================================================================");
    }

    private boolean isAtCentreOrUnset(double lat, double lon) {
        if (lat == 0.0 && lon == 0.0) {
            return true;
        }
        // Match default India centre (20.5937, 78.9629) within floating-point tolerance
        return Math.abs(lat - 20.5937) < 0.001 && Math.abs(lon - 78.9629) < 0.001;
    }
}
