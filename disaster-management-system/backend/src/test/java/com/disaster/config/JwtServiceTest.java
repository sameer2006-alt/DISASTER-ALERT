package com.disaster.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    @Test
    @DisplayName("Should fail fast with IllegalStateException when secret is missing or null")
    void testMissingSecretFailsFast() {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secret", null);

        IllegalStateException ex = assertThrows(IllegalStateException.class, service::validateSecret);
        assertTrue(ex.getMessage().contains("JWT_SECRET"));
    }

    @Test
    @DisplayName("Should fail fast with IllegalStateException when secret is shorter than 32 bytes")
    void testShortSecretFailsFast() {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secret", "short-secret-under-32-bytes!");

        IllegalStateException ex = assertThrows(IllegalStateException.class, service::validateSecret);
        assertTrue(ex.getMessage().contains("shorter than 32 bytes"));
    }

    @Test
    @DisplayName("Should succeed and sign/verify tokens when secret is at least 32 bytes")
    void testValidSecretSignsAndVerifies() {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secret", "a-very-secure-random-token-secret-that-exceeds-32-bytes");
        ReflectionTestUtils.setField(service, "expirationMs", 3600000L);
        service.validateSecret();

        String token = service.generateToken("testuser", Map.of("role", "CITIZEN"));
        assertNotNull(token);
        assertFalse(token.isBlank());
        assertTrue(service.isTokenValid(token));
        assertTrue("testuser".equals(service.extractUsername(token)));
        assertTrue("CITIZEN".equals(service.extractRole(token)));
    }
}

