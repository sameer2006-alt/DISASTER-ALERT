package com.disaster.dto;

import com.disaster.model.GeoLocation;
import com.disaster.model.Organisation;
import com.disaster.model.User;
import com.disaster.model.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntitySerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("Serializing Organisation to JSON must never include password or bcrypt hash")
    void organisationSerializationExcludesPassword() throws Exception {
        Organisation org = Organisation.builder()
                .id("org-123")
                .organisationName("Red Cross Test")
                .email("contact@redcross.test")
                .password("$2a$10$e8w.SuperSecretBcryptHashString1234567890")
                .verified(true)
                .activeStatus(true)
                .latitude(19.076)
                .longitude(72.8777)
                .geoLocation(GeoLocation.of(19.076, 72.8777))
                .shelterCapacity(100)
                .supportTypes(List.of("MEDICAL", "FOOD"))
                .contactNumber("+919876543210")
                .createdAt(Instant.now())
                .build();

        String json = objectMapper.writeValueAsString(org);

        assertFalse(json.contains("password"), "JSON must not contain 'password' key");
        assertFalse(json.contains("SuperSecretBcryptHashString"), "JSON must not contain password hash value");
        assertTrue(json.contains("org-123"));
        assertTrue(json.contains("Red Cross Test"));
    }

    @Test
    @DisplayName("Serializing User to JSON must never include password or bcrypt hash")
    void userSerializationExcludesPassword() throws Exception {
        User user = User.builder()
                .id("user-456")
                .username("citizen_john")
                .email("john@example.com")
                .password("$2a$10$k1w.SecretCitizenPasswordHash987654321")
                .role(UserRole.CITIZEN)
                .city("Mumbai")
                .state("Maharashtra")
                .verified(true)
                .latitude(19.076)
                .longitude(72.8777)
                .createdAt(Instant.now())
                .build();

        String json = objectMapper.writeValueAsString(user);

        assertFalse(json.contains("password"), "JSON must not contain 'password' key");
        assertFalse(json.contains("SecretCitizenPasswordHash"), "JSON must not contain user password hash");
        assertTrue(json.contains("user-456"));
        assertTrue(json.contains("citizen_john"));
    }
}

