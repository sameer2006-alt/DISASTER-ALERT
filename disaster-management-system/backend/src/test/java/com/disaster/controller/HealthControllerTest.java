package com.disaster.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class HealthControllerTest {

    @Test
    @DisplayName("Should return UP status for /api/health endpoint")
    void testHealthEndpoint() {
        HealthController controller = new HealthController();
        ResponseEntity<?> response = controller.health();

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals("UP", body.get("status"));
        assertEquals("Smart Disaster Alert System", body.get("service"));
    }
}
