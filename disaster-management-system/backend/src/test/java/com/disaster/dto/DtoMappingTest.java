package com.disaster.dto;

import com.disaster.model.GeoLocation;
import com.disaster.model.Organisation;
import com.disaster.model.RescueRequest;
import com.disaster.model.Shelter;
import com.disaster.model.Volunteer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DtoMappingTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("OrgPublicView must expose public info and omit password, email, and contactNumber")
    void testOrgPublicViewMapping() throws Exception {
        Organisation org = Organisation.builder()
                .id("org-99")
                .organisationName("Disaster Relief Foundation")
                .email("private_org_email@foundation.org")
                .password("$2a$10$SecretPasswordHash")
                .verified(true)
                .logoUrl("https://example.com/logo.png")
                .description("Providing disaster shelter and hot meals.")
                .country("India")
                .state("Maharashtra")
                .city("Mumbai")
                .headquartersLocation("BKC, Mumbai")
                .operatingLocations(List.of("Mumbai", "Thane"))
                .supportTypes(List.of("SHELTER", "FOOD"))
                .shelterCapacity(500)
                .foodCapacity(1000)
                .medicalCapacity(200)
                .activeStatus(true)
                .latitude(19.076)
                .longitude(72.8777)
                .geoLocation(GeoLocation.of(19.076, 72.8777))
                .contactNumber("+919999888877")
                .website("https://foundation.org")
                .resourcesAvailable(List.of("Tents", "Ambulances"))
                .verificationBadge("VERIFIED")
                .createdAt(Instant.now())
                .build();

        OrgPublicView view = OrgPublicView.from(org);

        assertNotNull(view);
        assertEquals("org-99", view.id());
        assertEquals("Disaster Relief Foundation", view.organisationName());
        assertEquals("https://example.com/logo.png", view.logoUrl());
        assertEquals("Providing disaster shelter and hot meals.", view.description());
        assertEquals("India", view.country());
        assertEquals("Maharashtra", view.state());
        assertEquals("Mumbai", view.city());
        assertEquals("BKC, Mumbai", view.headquartersLocation());
        assertEquals(List.of("Mumbai", "Thane"), view.operatingLocations());
        assertEquals(List.of("SHELTER", "FOOD"), view.supportTypes());
        assertEquals(500, view.shelterCapacity());
        assertEquals(1000, view.foodCapacity());
        assertEquals(200, view.medicalCapacity());
        assertTrue(view.activeStatus());
        assertEquals(19.076, view.latitude());
        assertEquals(72.8777, view.longitude());
        assertEquals(GeoLocation.of(19.076, 72.8777), view.geoLocation());
        assertEquals("https://foundation.org", view.website());
        assertEquals(List.of("Tents", "Ambulances"), view.resourcesAvailable());
        assertEquals("VERIFIED", view.verificationBadge());
        assertTrue(view.verified());

        // Verify class methods/components do NOT expose sensitive fields
        List<String> methodNames = Arrays.stream(OrgPublicView.class.getMethods())
                .map(Method::getName)
                .toList();
        assertFalse(methodNames.contains("password"), "OrgPublicView must not have password method");
        assertFalse(methodNames.contains("email"), "OrgPublicView must not have email method");
        assertFalse(methodNames.contains("contactNumber"), "OrgPublicView must not have contactNumber method");

        // Verify serialized JSON omits sensitive fields
        String json = objectMapper.writeValueAsString(view);
        assertFalse(json.contains("password"));
        assertFalse(json.contains("private_org_email@foundation.org"));
        assertFalse(json.contains("+919999888877"));
        assertTrue(json.contains("Disaster Relief Foundation"));
    }

    @Test
    @DisplayName("VolunteerView must expose public info and omit contact/phone and userId")
    void testVolunteerViewMapping() throws Exception {
        Volunteer volunteer = Volunteer.builder()
                .id("vol-101")
                .userId("user-internal-id-999")
                .name("Jane Responder")
                .contact("+919876543210")
                .role("Paramedic")
                .skills(List.of("CPR", "First Aid"))
                .status(Volunteer.VolunteerStatus.AVAILABLE)
                .assignedDisasterId("disaster-55")
                .latitude(19.076)
                .longitude(72.8777)
                .geoLocation(GeoLocation.of(19.076, 72.8777))
                .build();

        VolunteerView view = VolunteerView.from(volunteer);

        assertNotNull(view);
        assertEquals("vol-101", view.id());
        assertEquals("Jane Responder", view.name());
        assertEquals("Paramedic", view.role());
        assertEquals(List.of("CPR", "First Aid"), view.skills());
        assertEquals(Volunteer.VolunteerStatus.AVAILABLE, view.status());
        assertEquals("disaster-55", view.assignedDisasterId());
        assertEquals(19.076, view.latitude());
        assertEquals(72.8777, view.longitude());
        assertEquals(GeoLocation.of(19.076, 72.8777), view.geoLocation());

        // Verify class components do not have contact or userId
        List<String> methodNames = Arrays.stream(VolunteerView.class.getMethods())
                .map(Method::getName)
                .toList();
        assertFalse(methodNames.contains("contact"), "VolunteerView must not expose contact");
        assertFalse(methodNames.contains("phone"), "VolunteerView must not expose phone");
        assertFalse(methodNames.contains("userId"), "VolunteerView must not expose userId");

        // Verify JSON serialization
        String json = objectMapper.writeValueAsString(view);
        assertFalse(json.contains("+919876543210"), "JSON must not contain contact phone number");
        assertFalse(json.contains("user-internal-id-999"), "JSON must not contain userId");
        assertTrue(json.contains("Jane Responder"));
        assertTrue(json.contains("Paramedic"));
    }

    @Test
    @DisplayName("VolunteerOrgView must include contact phone for org triage but omit userId")
    void testVolunteerOrgViewMapping() throws Exception {
        Volunteer volunteer = Volunteer.builder()
                .id("vol-202")
                .userId("internal-user-id-555")
                .name("Bob Rescuer")
                .contact("+919876500000")
                .role("Rescue Diver")
                .skills(List.of("Boat Navigation", "Deep Diving"))
                .status(Volunteer.VolunteerStatus.AVAILABLE)
                .assignedDisasterId("disaster-88")
                .latitude(19.076)
                .longitude(72.8777)
                .geoLocation(GeoLocation.of(19.076, 72.8777))
                .build();

        VolunteerOrgView orgView = VolunteerOrgView.from(volunteer);

        assertNotNull(orgView);
        assertEquals("vol-202", orgView.id());
        assertEquals("Bob Rescuer", orgView.name());
        assertEquals("Rescue Diver", orgView.role());
        assertEquals("+919876500000", orgView.contact());
        assertEquals(List.of("Boat Navigation", "Deep Diving"), orgView.skills());
        assertEquals(Volunteer.VolunteerStatus.AVAILABLE, orgView.status());

        List<String> methodNames = Arrays.stream(VolunteerOrgView.class.getMethods())
                .map(Method::getName)
                .toList();
        assertTrue(methodNames.contains("contact"), "VolunteerOrgView must contain contact");
        assertFalse(methodNames.contains("userId"), "VolunteerOrgView must omit userId");

        String json = objectMapper.writeValueAsString(orgView);
        assertTrue(json.contains("+919876500000"), "JSON must contain contact for authenticated org view");
        assertFalse(json.contains("internal-user-id-555"), "JSON must omit userId");
    }

    @Test
    @DisplayName("ShelterView must expose public capacity, bed, and status details")
    void testShelterViewMapping() throws Exception {
        Shelter shelter = Shelter.builder()
                .id("shelter-1")
                .name("Central Community Relief Hub")
                .organisationId("org-99")
                .capacity(400)
                .availableBeds(150)
                .foodAvailable(true)
                .medicalAvailable(true)
                .latitude(19.076)
                .longitude(72.8777)
                .geoLocation(GeoLocation.of(19.076, 72.8777))
                .contactDetails("1077 (Toll-Free Helpline)")
                .status(Shelter.ShelterStatus.ACTIVE)
                .build();

        ShelterView view = ShelterView.from(shelter);

        assertNotNull(view);
        assertEquals("shelter-1", view.id());
        assertEquals("Central Community Relief Hub", view.name());
        assertEquals("org-99", view.organisationId());
        assertEquals(400, view.capacity());
        assertEquals(150, view.availableBeds());
        assertTrue(view.foodAvailable());
        assertTrue(view.medicalAvailable());
        assertEquals(19.076, view.latitude());
        assertEquals(72.8777, view.longitude());
        assertEquals("1077 (Toll-Free Helpline)", view.contactDetails());
        assertEquals(Shelter.ShelterStatus.ACTIVE, view.status());

        String json = objectMapper.writeValueAsString(view);
        assertFalse(json.contains("password"));
        assertTrue(json.contains("Central Community Relief Hub"));
    }

    @Test
    @DisplayName("RescueView must omit requester userId and expose safe coordinates and priority")
    void testRescueViewMapping() throws Exception {
        RescueRequest req = RescueRequest.builder()
                .id("rescue-7")
                .userId("private-user-id-321")
                .description("Family trapped on 2nd floor due to flash flooding")
                .priority("HIGH")
                .latitude(19.076)
                .longitude(72.8777)
                .geoLocation(GeoLocation.of(19.076, 72.8777))
                .status(RescueRequest.RescueStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        RescueView view = RescueView.from(req);

        assertNotNull(view);
        assertEquals("rescue-7", view.id());
        assertEquals("Family trapped on 2nd floor due to flash flooding", view.description());
        assertEquals("HIGH", view.priority());
        assertEquals(19.076, view.latitude());
        assertEquals(72.8777, view.longitude());
        assertEquals(RescueRequest.RescueStatus.PENDING, view.status());

        List<String> methodNames = Arrays.stream(RescueView.class.getMethods())
                .map(Method::getName)
                .toList();
        assertFalse(methodNames.contains("userId"), "RescueView must not expose userId");

        String json = objectMapper.writeValueAsString(view);
        assertFalse(json.contains("private-user-id-321"), "JSON must not contain userId");
        assertFalse(json.contains("password"));
        assertTrue(json.contains("rescue-7"));
    }
}

