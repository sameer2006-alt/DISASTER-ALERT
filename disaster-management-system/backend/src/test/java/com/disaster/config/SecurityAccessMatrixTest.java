package com.disaster.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = {
                SecurityAccessMatrixTest.TestConfig.class,
                SecurityConfig.class,
                CorsConfig.class,
                CustomAuthenticationEntryPoint.class,
                CustomAccessDeniedHandler.class,
                JwtAuthenticationFilter.class,
                IngestApiKeyFilter.class
        },
        properties = {
                "app.ingest.api-key=test-ingest-api-key-for-security-matrix"
        }
)
@AutoConfigureMockMvc
class SecurityAccessMatrixTest {

    @Configuration
    @EnableWebMvc
    static class TestConfig {
        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        public TestSecurityController testSecurityController() {
            return new TestSecurityController();
        }
    }

    @RestController
    static class TestSecurityController {
        @GetMapping("/api/health")
        public Map<String, String> health() {
            return Map.of("status", "UP");
        }

        @GetMapping("/api/simulate/active")
        public Map<String, String> simulateActive() {
            return Map.of("status", "ACTIVE");
        }

        @PostMapping("/api/rescue/request")
        public Map<String, String> rescueRequest() {
            return Map.of("status", "SOS_ACCEPTED");
        }

        @PostMapping("/api/events/ingest")
        public Map<String, String> eventsIngest() {
            return Map.of("status", "INGEST_OK");
        }

        @PostMapping("/api/simulate/flood")
        public Map<String, String> simulateFlood() {
            return Map.of("status", "FLOOD_SIMULATED");
        }

        @PostMapping("/api/verification/reset")
        public Map<String, String> verificationReset() {
            return Map.of("status", "RESET_OK");
        }

        @GetMapping("/api/rescue")
        public List<String> rescueList() {
            return List.of("req-1");
        }

        @GetMapping("/api/ai/threat-analysis")
        public Map<String, String> aiAnalysis() {
            return Map.of("status", "THREAT_ANALYSIS_OK");
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    private static final String CITIZEN_TOKEN = "valid-citizen-jwt";
    private static final String ORG_TOKEN = "valid-org-jwt";
    private static final String ADMIN_TOKEN = "valid-admin-jwt";
    private static final String BAD_TOKEN = "invalid-jwt-token";

    @BeforeEach
    void setUpJwtMocks() {
        // Citizen token mock (role bare name)
        when(jwtService.isTokenValid(CITIZEN_TOKEN)).thenReturn(true);
        when(jwtService.extractUsername(CITIZEN_TOKEN)).thenReturn("citizen1");
        when(jwtService.extractRole(CITIZEN_TOKEN)).thenReturn("CITIZEN");

        // Organisation token mock (bare name "ORGANISATION" stored in token)
        when(jwtService.isTokenValid(ORG_TOKEN)).thenReturn(true);
        when(jwtService.extractUsername(ORG_TOKEN)).thenReturn("org1@example.org");
        when(jwtService.extractRole(ORG_TOKEN)).thenReturn("ORGANISATION");

        // Admin token mock
        when(jwtService.isTokenValid(ADMIN_TOKEN)).thenReturn(true);
        when(jwtService.extractUsername(ADMIN_TOKEN)).thenReturn("admin");
        when(jwtService.extractRole(ADMIN_TOKEN)).thenReturn("ADMIN");

        // Bad token mock
        when(jwtService.isTokenValid(BAD_TOKEN)).thenReturn(false);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Row 1: Public GET (/api/health, /api/simulate/active)
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Row 1: Public GET")
    class PublicGetTests {

        @Test
        @DisplayName("Anonymous can access GET /api/health")
        void anonymousAccessHealth() throws Exception {
            mockMvc.perform(get("/api/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"));
        }

        @Test
        @DisplayName("Citizen can access GET /api/health")
        void citizenAccessHealth() throws Exception {
            mockMvc.perform(get("/api/health")
                            .header("Authorization", "Bearer " + CITIZEN_TOKEN))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Organisation can access GET /api/health")
        void orgAccessHealth() throws Exception {
            mockMvc.perform(get("/api/health")
                            .header("Authorization", "Bearer " + ORG_TOKEN))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Admin can access GET /api/health")
        void adminAccessHealth() throws Exception {
            mockMvc.perform(get("/api/health")
                            .header("Authorization", "Bearer " + ADMIN_TOKEN))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Anonymous can access GET /api/simulate/active")
        void anonymousAccessSimulateActive() throws Exception {
            mockMvc.perform(get("/api/simulate/active"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Row 2: Public POST (e.g. SOS /api/rescue/request)
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Row 2: Public POST (/api/rescue/request SOS)")
    class PublicPostTests {

        @Test
        @DisplayName("Anonymous can invoke POST /api/rescue/request (status 200 OK)")
        void anonymousCanPostSosRequest() throws Exception {
            mockMvc.perform(post("/api/rescue/request")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SOS_ACCEPTED"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Row 2b: Ingest Endpoint (Guarded by IngestApiKeyFilter / X-Ingest-Key)
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Row 2b: Ingest Endpoint (guarded by X-Ingest-Key)")
    class IngestEndpointSecurityTests {

        @Test
        @DisplayName("POST /api/events/ingest with no header returns 401 JSON")
        void ingestWithoutHeaderReturns401() throws Exception {
            mockMvc.perform(post("/api/events/ingest")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").value("Unauthorized"))
                    .andExpect(jsonPath("$.message").value("Invalid or missing X-Ingest-Key header"));
        }

        @Test
        @DisplayName("POST /api/events/ingest with wrong header returns 401 JSON")
        void ingestWithWrongHeaderReturns401() throws Exception {
            mockMvc.perform(post("/api/events/ingest")
                            .header("X-Ingest-Key", "invalid-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").value("Unauthorized"))
                    .andExpect(jsonPath("$.message").value("Invalid or missing X-Ingest-Key header"));
        }

        @Test
        @DisplayName("POST /api/events/ingest with valid X-Ingest-Key header returns 200 OK")
        void ingestWithValidHeaderReturns200() throws Exception {
            mockMvc.perform(post("/api/events/ingest")
                            .header("X-Ingest-Key", "test-ingest-api-key-for-security-matrix")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("INGEST_OK"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Row 3: ADMIN only (/api/verification/reset, /api/simulate/flood)
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Row 3: ADMIN only (/api/verification/reset, /api/simulate/flood)")
    class AdminOnlyTests {

        @Test
        @DisplayName("Anonymous to POST /api/verification/reset returns 401 JSON")
        void anonymousToVerificationResetReturns401() throws Exception {
            mockMvc.perform(post("/api/verification/reset"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").value("Unauthorized"))
                    .andExpect(jsonPath("$.path").value("/api/verification/reset"));
        }

        @Test
        @DisplayName("Citizen to POST /api/simulate/flood returns 403 JSON")
        void citizenToSimulateFloodReturns403() throws Exception {
            mockMvc.perform(post("/api/simulate/flood")
                            .header("Authorization", "Bearer " + CITIZEN_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.error").value("Forbidden"))
                    .andExpect(jsonPath("$.path").value("/api/simulate/flood"));
        }

        @Test
        @DisplayName("Organisation to POST /api/simulate/flood returns 403 JSON")
        void orgToSimulateFloodReturns403() throws Exception {
            mockMvc.perform(post("/api/simulate/flood")
                            .header("Authorization", "Bearer " + ORG_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.error").value("Forbidden"));
        }

        @Test
        @DisplayName("Admin to POST /api/verification/reset returns 200 OK")
        void adminToVerificationResetReturns200() throws Exception {
            mockMvc.perform(post("/api/verification/reset")
                            .header("Authorization", "Bearer " + ADMIN_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("RESET_OK"));
        }

        @Test
        @DisplayName("Admin to POST /api/simulate/flood returns 200 OK")
        void adminToSimulateFloodReturns200() throws Exception {
            mockMvc.perform(post("/api/simulate/flood")
                            .header("Authorization", "Bearer " + ADMIN_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("FLOOD_SIMULATED"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Row 4: ORGANISATION or ADMIN (GET /api/rescue)
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Row 4: ORGANISATION or ADMIN (GET /api/rescue)")
    class OrgOrAdminTests {

        @Test
        @DisplayName("Anonymous to GET /api/rescue returns 401 JSON")
        void anonymousToRescueReturns401() throws Exception {
            mockMvc.perform(get("/api/rescue"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").value("Unauthorized"));
        }

        @Test
        @DisplayName("Citizen to GET /api/rescue returns 403 JSON")
        void citizenToRescueReturns403() throws Exception {
            mockMvc.perform(get("/api/rescue")
                            .header("Authorization", "Bearer " + CITIZEN_TOKEN))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.error").value("Forbidden"));
        }

        @Test
        @DisplayName("Organisation to GET /api/rescue returns 200 OK")
        void orgToRescueReturns200() throws Exception {
            mockMvc.perform(get("/api/rescue")
                            .header("Authorization", "Bearer " + ORG_TOKEN))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Admin to GET /api/rescue returns 200 OK")
        void adminToRescueReturns200() throws Exception {
            mockMvc.perform(get("/api/rescue")
                            .header("Authorization", "Bearer " + ADMIN_TOKEN))
                    .andExpect(status().isOk());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Row 5: Authenticated (/api/ai/threat-analysis)
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Row 5: Authenticated (/api/ai/threat-analysis)")
    class AuthenticatedTests {

        @Test
        @DisplayName("Anonymous to GET /api/ai/threat-analysis returns 401 JSON")
        void anonymousToAiAnalysisReturns401() throws Exception {
            mockMvc.perform(get("/api/ai/threat-analysis"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").value("Unauthorized"));
        }

        @Test
        @DisplayName("Citizen to GET /api/ai/threat-analysis returns 200 OK")
        void citizenToAiAnalysisReturns200() throws Exception {
            mockMvc.perform(get("/api/ai/threat-analysis")
                            .header("Authorization", "Bearer " + CITIZEN_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("THREAT_ANALYSIS_OK"));
        }

        @Test
        @DisplayName("Organisation to GET /api/ai/threat-analysis returns 200 OK")
        void orgToAiAnalysisReturns200() throws Exception {
            mockMvc.perform(get("/api/ai/threat-analysis")
                            .header("Authorization", "Bearer " + ORG_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("THREAT_ANALYSIS_OK"));
        }

        @Test
        @DisplayName("Admin to GET /api/ai/threat-analysis returns 200 OK")
        void adminToAiAnalysisReturns200() throws Exception {
            mockMvc.perform(get("/api/ai/threat-analysis")
                            .header("Authorization", "Bearer " + ADMIN_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("THREAT_ANALYSIS_OK"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Row 6: Default Deny / Authenticated (Unknown/Unlisted endpoints)
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Row 6: Default deny on unknown or unlisted endpoint")
    class DefaultDenyTests {

        @Test
        @DisplayName("Anonymous to unknown endpoint returns 401 JSON")
        void anonymousToUnknownEndpointReturns401() throws Exception {
            mockMvc.perform(get("/api/unknown/unlisted-resource"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").value("Unauthorized"));
        }

        @Test
        @DisplayName("Invalid token to protected endpoint returns 401 JSON")
        void badTokenReturns401() throws Exception {
            mockMvc.perform(post("/api/verification/reset")
                            .header("Authorization", "Bearer " + BAD_TOKEN))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").value("Unauthorized"));
        }
    }
}

