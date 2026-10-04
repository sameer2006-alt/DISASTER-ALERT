package com.disaster.config;

import com.disaster.model.*;
import com.disaster.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.GeospatialIndex;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;

@Component
@Profile("dev")
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final OrganisationRepository organisationRepository;
    private final ShelterRepository shelterRepository;
    private final VolunteerRepository volunteerRepository;
    private final PasswordEncoder passwordEncoder;
    private final MongoTemplate mongoTemplate;

    public DataSeeder(
            UserRepository userRepository,
            OrganisationRepository organisationRepository,
            ShelterRepository shelterRepository,
            VolunteerRepository volunteerRepository,
            PasswordEncoder passwordEncoder,
            MongoTemplate mongoTemplate) {
        this.userRepository = userRepository;
        this.organisationRepository = organisationRepository;
        this.shelterRepository = shelterRepository;
        this.volunteerRepository = volunteerRepository;
        this.passwordEncoder = passwordEncoder;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(String... args) {
        log.warn("===============================================================================");
        log.warn("WARNING: Running with 'dev' Spring profile active! Mock seeding is enabled.");
        log.warn("DO NOT RUN WITH THE 'dev' PROFILE IN PRODUCTION ENVIRONMENTS!");
        log.warn("===============================================================================");

        // Resolve development seed passwords
        String adminPassword = System.getenv("DEV_ADMIN_PASSWORD");
        if (adminPassword == null || adminPassword.isBlank()) {
            adminPassword = generateRandomPassword(16);
            log.warn("DEV PROFILE: No DEV_ADMIN_PASSWORD set. Generated random admin password: {}", adminPassword);
        }

        String userPassword = System.getenv("DEV_USER_PASSWORD");
        if (userPassword == null || userPassword.isBlank()) {
            userPassword = generateRandomPassword(16);
            log.warn("DEV PROFILE: No DEV_USER_PASSWORD set. Generated random user password: {}", userPassword);
        }

        String orgPassword = System.getenv("DEV_ORG_PASSWORD");
        if (orgPassword == null || orgPassword.isBlank()) {
            orgPassword = generateRandomPassword(16);
            log.warn("DEV PROFILE: No DEV_ORG_PASSWORD set. Generated random org password: {}", orgPassword);
        }

        // Ensure 2dsphere indexes compile programmatically as runtime safety net
        try {
            mongoTemplate.indexOps(User.class).ensureIndex(new GeospatialIndex("geoLocation").typed(org.springframework.data.mongodb.core.index.GeoSpatialIndexType.GEO_2DSPHERE));
            mongoTemplate.indexOps(Shelter.class).ensureIndex(new GeospatialIndex("geoLocation").typed(org.springframework.data.mongodb.core.index.GeoSpatialIndexType.GEO_2DSPHERE));
            mongoTemplate.indexOps(Volunteer.class).ensureIndex(new GeospatialIndex("geoLocation").typed(org.springframework.data.mongodb.core.index.GeoSpatialIndexType.GEO_2DSPHERE));
            mongoTemplate.indexOps(Organisation.class).ensureIndex(new GeospatialIndex("geoLocation").typed(org.springframework.data.mongodb.core.index.GeoSpatialIndexType.GEO_2DSPHERE));
            mongoTemplate.indexOps(DisasterEvent.class).ensureIndex(new GeospatialIndex("geoLocation").typed(org.springframework.data.mongodb.core.index.GeoSpatialIndexType.GEO_2DSPHERE));
            log.info("Programmatic 2dsphere index verification successful.");
        } catch (Exception e) {
            log.warn("Could not create geospatial indexes programmatically: {}", e.getMessage());
        }

        // 1. Seed Admin
        if (!userRepository.existsByUsername("admin")) {
            userRepository.save(User.builder()
                    .username("admin")
                    .email("admin@example.org")
                    .password(passwordEncoder.encode(adminPassword))
                    .location("HQ")
                    .state("Delhi")
                    .city("New Delhi")
                    .role(UserRole.ADMIN)
                    .verified(true)
                    .latitude(28.6139)
                    .longitude(77.2090)
                    .geoLocation(GeoLocation.of(28.6139, 77.2090))
                    .createdAt(Instant.now())
                    .build());
        }

        // 2. Seed Test Citizen Users inside Mumbai region to receive disaster email alerts
        if (!userRepository.existsByEmail("citizen1@example.org")) {
            User citizen1 = User.builder()
                    .username("citizen1")
                    .email("citizen1@example.org")
                    .password(passwordEncoder.encode(userPassword))
                    .location("Mumbai Central")
                    .state("Maharashtra")
                    .city("Mumbai")
                    .role(UserRole.CITIZEN)
                    .verified(true)
                    .latitude(19.0760)
                    .longitude(72.8777)
                    .geoLocation(GeoLocation.of(19.0760, 72.8777))
                    .createdAt(Instant.now())
                    .build();
            userRepository.save(citizen1);
        }

        if (!userRepository.existsByEmail("citizen2@example.org")) {
            User citizen2 = User.builder()
                    .username("citizen2")
                    .email("citizen2@example.org")
                    .password(passwordEncoder.encode(userPassword))
                    .location("Bandra Waterfront")
                    .state("Maharashtra")
                    .city("Mumbai")
                    .role(UserRole.CITIZEN)
                    .verified(true)
                    .latitude(19.0490)
                    .longitude(72.8130)
                    .geoLocation(GeoLocation.of(19.0490, 72.8130))
                    .createdAt(Instant.now())
                    .build();
            userRepository.save(citizen2);
        }

        // 3. Seed Organisation
        if (organisationRepository.count() == 0) {
            Organisation ngo = Organisation.builder()
                    .organisationName("Rapid Relief NGO")
                    .email("relief@example.org")
                    .password(passwordEncoder.encode(orgPassword))
                    .verified(true)
                    .activeStatus(true)
                    .country("India")
                    .state("Maharashtra")
                    .city("Mumbai")
                    .description("Emergency food and shelter provider")
                    .supportTypes(List.of("Food", "Shelter", "Drinking Water", "Rescue Boats", "Evacuation Transport"))
                    .resourcesAvailable(List.of("Food", "Blankets", "Rescue Boats"))
                    .shelterCapacity(500)
                    .latitude(19.0760)
                    .longitude(72.8777)
                    .verificationBadge("VERIFIED")
                    .createdAt(Instant.now())
                    .build();
            ngo.syncGeo();
            organisationRepository.save(ngo);

            // 4. Seed Shelters (Active nationwide relief shelters)
            List<Shelter> seedShelters = List.of(
                    Shelter.builder()
                            .name("Mumbai Central Relief Hub")
                            .organisationId(ngo.getId())
                            .capacity(500)
                            .availableBeds(340)
                            .foodAvailable(true)
                            .medicalAvailable(true)
                            .latitude(19.0176)
                            .longitude(72.8562)
                            .contactDetails("+91-22-2269-4725")
                            .status(Shelter.ShelterStatus.ACTIVE)
                            .build(),
                    Shelter.builder()
                            .name("Kurla Emergency Evacuation Camp")
                            .organisationId(ngo.getId())
                            .capacity(450)
                            .availableBeds(280)
                            .foodAvailable(true)
                            .medicalAvailable(true)
                            .latitude(19.0728)
                            .longitude(72.8826)
                            .contactDetails("+91-22-2650-1122")
                            .status(Shelter.ShelterStatus.ACTIVE)
                            .build(),
                    Shelter.builder()
                            .name("New Delhi Red Cross Emergency Shelter")
                            .organisationId(ngo.getId())
                            .capacity(750)
                            .availableBeds(520)
                            .foodAvailable(true)
                            .medicalAvailable(true)
                            .latitude(28.6139)
                            .longitude(77.2090)
                            .contactDetails("+91-11-2371-6441")
                            .status(Shelter.ShelterStatus.ACTIVE)
                            .build(),
                    Shelter.builder()
                            .name("Bhubaneswar State Disaster Relief Center")
                            .organisationId(ngo.getId())
                            .capacity(850)
                            .availableBeds(700)
                            .foodAvailable(true)
                            .medicalAvailable(true)
                            .latitude(20.2961)
                            .longitude(85.8245)
                            .contactDetails("+91-674-239-5398")
                            .status(Shelter.ShelterStatus.ACTIVE)
                            .build(),
                    Shelter.builder()
                            .name("Chennai Coastal Flood Relief Haven")
                            .organisationId(ngo.getId())
                            .capacity(700)
                            .availableBeds(540)
                            .foodAvailable(true)
                            .medicalAvailable(true)
                            .latitude(13.0827)
                            .longitude(80.2707)
                            .contactDetails("+91-44-2561-9206")
                            .status(Shelter.ShelterStatus.ACTIVE)
                            .build(),
                    Shelter.builder()
                            .name("Kolkata Netaji Emergency Shelter")
                            .organisationId(ngo.getId())
                            .capacity(950)
                            .availableBeds(820)
                            .foodAvailable(true)
                            .medicalAvailable(true)
                            .latitude(22.5697)
                            .longitude(88.3434)
                            .contactDetails("+91-33-2214-3526")
                            .status(Shelter.ShelterStatus.ACTIVE)
                            .build()
            );
            for (Shelter s : seedShelters) {
                s.syncGeo();
                shelterRepository.save(s);
            }
        }

        // 5. Seed Volunteer
        if (volunteerRepository.count() == 0) {
            User volunteerUser = User.builder()
                    .username("mumbaivolunteer")
                    .email("volunteer@example.org")
                    .password(passwordEncoder.encode(userPassword))
                    .location("Kurla")
                    .state("Maharashtra")
                    .city("Mumbai")
                    .role(UserRole.VOLUNTEER)
                    .verified(true)
                    .latitude(19.0728)
                    .longitude(72.8826)
                    .geoLocation(GeoLocation.of(19.0728, 72.8826))
                    .createdAt(Instant.now())
                    .build();
            userRepository.save(volunteerUser);

            Volunteer volunteer = Volunteer.builder()
                    .userId(volunteerUser.getId())
                    .status(Volunteer.VolunteerStatus.AVAILABLE)
                    .latitude(19.0728)
                    .longitude(72.8826)
                    .skills(List.of("First Aid", "Rescue Operations", "Drinking Water Supply"))
                    .build();
            volunteer.syncGeo();
            volunteerRepository.save(volunteer);
        }
    }

    private String generateRandomPassword(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
