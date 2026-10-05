package com.disaster.config;

import com.disaster.controller.AuthController;
import com.disaster.controller.GlobalExceptionHandler;
import com.disaster.controller.RescueController;
import com.disaster.dto.AuthResponse;
import com.disaster.dto.RescueView;
import com.disaster.model.RescueRequest;
import com.disaster.repository.OrganisationRepository;
import com.disaster.repository.RescueRequestRepository;
import com.disaster.service.AuthService;
import com.disaster.service.EmailService;
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
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Instant;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(
        classes = {
                RateLimitingIntegrationTest.TestConfig.class,
                SecurityConfig.class,
                CorsConfig.class,
                CustomAuthenticationEntryPoint.class,
                CustomAccessDeniedHandler.class,
                JwtAuthenticationFilter.class,
                IngestApiKeyFilter.class,
                RateLimiterService.class,
                RateLimitingFilter.class,
                AuthController.class,
                RescueController.class,
                GlobalExceptionHandler.class
        },
        properties = {
                "app.ingest.api-key=test-ingest-api-key-rate-limit-suite",
                "app.rate-limit.enabled=true",
                "app.rate-limit.auth.max-requests-per-minute-ip=5",
                "app.rate-limit.auth.max-requests-per-minute-email=5",
                "app.rate-limit.rescue.max-requests-per-minute-ip=5"
        }
)
@AutoConfigureMockMvc
public class RateLimitingIntegrationTest {

    @Configuration
    @EnableWebMvc
    static class TestConfig {
        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean
        public FaultTestController faultTestController() {
            return new FaultTestController();
        }
    }

    @RestController
    static class FaultTestController {
        @GetMapping("/api/test-fault/illegal-state")
        public String triggerIllegalState() {
            throw new IllegalStateException("Internal database transaction timeout");
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RateLimiterService rateLimiterService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AuthService authService;

    @MockBean
    private RescueRequestRepository rescueRepository;

    @MockBean
    private SimpMessagingTemplate messagingTemplate;

    @MockBean
    private OrganisationRepository organisationRepository;

    @MockBean
    private EmailService emailService;

    @MockBean
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() {
        rateLimiterService.clear();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Rate Limiting Tests (Login & Rescue)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("6th login attempt in 1 minute returns HTTP 429 with Retry-After header and JSON body")
    void testAuthRateLimiterBlocks6thLoginRequest() throws Exception {
        Map<String, String> loginBody = Map.of(
                "username", "testuser@example.com",
                "password", "secretPassword123"
        );
        String jsonPayload = objectMapper.writeValueAsString(loginBody);

        AuthResponse successResponse = new AuthResponse("mock-jwt-token", "testuser@example.com", "CITIZEN", "Login successful");
        when(authService.login("testuser@example.com", "secretPassword123")).thenReturn(successResponse);

        // Requests 1 through 5 must succeed (HTTP 200)
        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("mock-jwt-token"));
        }

        // The 6th request from the same IP/identifier within 1 minute must return HTTP 429
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber())
                .andExpect(jsonPath("$.message", containsString("Too many requests. Please try again in")));
    }

    @Test
    @DisplayName("6th rescue SOS beacon request from same IP returns HTTP 429 with Retry-After header")
    void testRescueRateLimiterBlocks6thRescueRequest() throws Exception {
        Map<String, Object> rescueBody = Map.of(
                "description", "Trapped in flood waters",
                "latitude", 19.076,
                "longitude", 72.877
        );
        String jsonPayload = objectMapper.writeValueAsString(rescueBody);

        RescueRequest mockSaved = RescueRequest.builder()
                .id("rescue-gen-101")
                .description("Trapped in flood waters")
                .latitude(19.076)
                .longitude(72.877)
                .status(RescueRequest.RescueStatus.PENDING)
                .createdAt(Instant.now())
                .build();
        when(rescueRepository.save(any(RescueRequest.class))).thenReturn(mockSaved);

        // Requests 1 to 5 succeed
        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/api/rescue/request")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value("rescue-gen-101"));
        }

        // 6th request from same IP returns 429
        mockMvc.perform(post("/api/rescue/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Error Handling Tests (IllegalStateException & BadCredentialsException)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("Unrelated IllegalStateException returns HTTP 500 with correlationId and generic message (NOT 429)")
    void testUnrelatedIllegalStateExceptionReturns500WithCorrelationId() throws Exception {
        mockMvc.perform(get("/api/test-fault/illegal-state"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal server error"))
                .andExpect(jsonPath("$.correlationId", notNullValue()))
                .andExpect(jsonPath("$.message", containsString("Reference ID:")))
                // Ensure the raw exception message is NOT leaked to clients
                .andExpect(jsonPath("$.message", not(containsString("Internal database transaction timeout"))));
    }

    @Test
    @DisplayName("Invalid login credentials returns HTTP 401 (not 400 or 500)")
    void testInvalidCredentialsReturns401() throws Exception {
        when(authService.login("victim@example.com", "wrongpassword"))
                .thenThrow(new BadCredentialsException("Invalid credentials"));

        Map<String, String> body = Map.of(
                "username", "victim@example.com",
                "password", "wrongpassword"
        );

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid credentials"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Unit Tests on RateLimiterService directly
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("RateLimiterService Direct Unit Tests")
    class RateLimiterServiceUnitTests {

        @Test
        @DisplayName("Rate limiter enforces per-identifier limit even across different client IPs")
        void testPerIdentifierLimitEnforcedAcrossIps() {
            RateLimiterService service = new RateLimiterService();
            service.setAuthMaxPerMinuteIp(100);     // High IP limit
            service.setAuthMaxPerMinuteEmail(3);    // Low email limit

            // Requests 1-3 from 3 different IPs for the same user succeed
            assertTrue(service.checkAuth("1.1.1.1", "victim@example.com").allowed());
            assertTrue(service.checkAuth("2.2.2.2", "victim@example.com").allowed());
            assertTrue(service.checkAuth("3.3.3.3", "victim@example.com").allowed());

            // 4th request for the same email from a 4th IP is blocked
            RateLimiterService.RateLimitResult result = service.checkAuth("4.4.4.4", "victim@example.com");
            assertFalse(result.allowed());
            assertTrue(result.retryAfterSeconds() > 0);
        }

        @Test
        @DisplayName("Rate limiter allows requests when disabled")
        void testRateLimiterCanBeDisabled() {
            RateLimiterService service = new RateLimiterService();
            service.setEnabled(false);
            service.setAuthMaxPerMinuteIp(1);

            assertTrue(service.checkAuth("1.2.3.4", "user@example.com").allowed());
            assertTrue(service.checkAuth("1.2.3.4", "user@example.com").allowed());
            assertTrue(service.checkRescue("1.2.3.4").allowed());
            assertTrue(service.checkRescue("1.2.3.4").allowed());
        }
    }
}

