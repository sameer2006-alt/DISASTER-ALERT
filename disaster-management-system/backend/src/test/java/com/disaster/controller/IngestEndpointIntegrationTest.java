package com.disaster.controller;

import com.disaster.config.CorsConfig;
import com.disaster.config.CustomAccessDeniedHandler;
import com.disaster.config.CustomAuthenticationEntryPoint;
import com.disaster.config.IngestApiKeyFilter;
import com.disaster.config.JwtAuthenticationFilter;
import com.disaster.config.JwtService;
import com.disaster.config.SecurityConfig;
import com.disaster.model.DisasterEvent;
import com.disaster.model.DisasterType;
import com.disaster.model.EventSource;
import com.disaster.repository.DisasterEventRepository;
import com.disaster.service.EventProcessorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = {
                IngestEndpointIntegrationTest.TestContextConfig.class,
                SecurityConfig.class,
                CorsConfig.class,
                CustomAuthenticationEntryPoint.class,
                CustomAccessDeniedHandler.class,
                JwtAuthenticationFilter.class,
                IngestApiKeyFilter.class,
                EventController.class,
                GlobalExceptionHandler.class
        },
        properties = {
                "app.ingest.api-key=" + IngestEndpointIntegrationTest.TEST_INGEST_KEY
        }
)
@AutoConfigureMockMvc
public class IngestEndpointIntegrationTest {

    public static final String TEST_INGEST_KEY = "test-secret-key-for-ingest-32b!!";

    @Configuration
    @org.springframework.web.servlet.config.annotation.EnableWebMvc
    static class TestContextConfig {
        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private EventProcessorService eventProcessor;

    @MockBean
    private DisasterEventRepository disasterRepository;

    @Test
    @DisplayName("No X-Ingest-Key header -> 401 Unauthorized JSON")
    void testIngestMissingHeader() throws Exception {
        Map<String, Object> body = Map.of(
                "disasterType", "FLOOD",
                "severity", 7,
                "latitude", 19.076,
                "longitude", 72.877,
                "message", "Flash flood alert in suburban region"
        );

        mockMvc.perform(post("/api/events/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid or missing X-Ingest-Key header"))
                .andExpect(jsonPath("$.path").value("/api/events/ingest"));
    }

    @Test
    @DisplayName("Wrong X-Ingest-Key header -> 401 Unauthorized JSON")
    void testIngestWrongHeader() throws Exception {
        Map<String, Object> body = Map.of(
                "disasterType", "FLOOD",
                "severity", 7,
                "latitude", 19.076,
                "longitude", 72.877,
                "message", "Flash flood alert in suburban region"
        );

        mockMvc.perform(post("/api/events/ingest")
                        .header("X-Ingest-Key", "totally-invalid-key-xyz")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid or missing X-Ingest-Key header"));
    }

    @Test
    @DisplayName("Correct X-Ingest-Key header + valid body -> 200 OK with processed event")
    void testIngestValidKeyAndBody() throws Exception {
        DisasterEvent mockSaved = DisasterEvent.builder()
                .id("sachet-evt-1001")
                .disasterType(DisasterType.FLOOD)
                .severity(7)
                .location("Mumbai, India")
                .latitude(19.076)
                .longitude(72.877)
                .message("Flash flood alert in suburban region")
                .affectedRadius(25.0)
                .source(EventSource.SACHET_NDMA)
                .active(true)
                .build();

        when(eventProcessor.processEvent(any(DisasterEvent.class))).thenReturn(mockSaved);

        Map<String, Object> validBody = Map.of(
                "id", "sachet-evt-1001",
                "disasterType", "FLOOD",
                "severity", 7,
                "location", "Mumbai, India",
                "latitude", 19.076,
                "longitude", 72.877,
                "message", "Flash flood alert in suburban region",
                "affectedRadius", 25.0,
                "source", "SACHET_NDMA"
        );

        mockMvc.perform(post("/api/events/ingest")
                        .header("X-Ingest-Key", TEST_INGEST_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("sachet-evt-1001"))
                .andExpect(jsonPath("$.disasterType").value("FLOOD"))
                .andExpect(jsonPath("$.severity").value(7))
                .andExpect(jsonPath("$.source").value("SACHET_NDMA"));
    }

    @Test
    @DisplayName("Correct X-Ingest-Key header + invalid body (severity=99, lat=200, missing type) -> 400 Bad Request with field errors")
    void testIngestInvalidBody() throws Exception {
        Map<String, Object> invalidBody = Map.of(
                "severity", 99,
                "latitude", 200.0,
                "longitude", 500.0,
                "message", "" // blank message
                // missing disasterType
        );

        mockMvc.perform(post("/api/events/ingest")
                        .header("X-Ingest-Key", TEST_INGEST_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidBody)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.disasterType", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors.severity", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors.latitude", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors.longitude", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors.message", notNullValue()));
    }
}
