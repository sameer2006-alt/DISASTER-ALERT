package com.disaster.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Security servlet filter protecting POST /api/events/ingest.
 * Requires the X-Ingest-Key header matching the shared secret INGEST_API_KEY.
 * Employs constant-time byte comparison (MessageDigest.isEqual) to prevent timing side-channel attacks.
 * Rejects unauthorized calls with an RFC-compliant 401 JSON response matching CustomAuthenticationEntryPoint.
 */
@Component
public class IngestApiKeyFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    @Value("${app.ingest.api-key:}")
    private String configuredApiKey;

    @org.springframework.beans.factory.annotation.Autowired
    public IngestApiKeyFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public IngestApiKeyFilter(ObjectMapper objectMapper, String configuredApiKey) {
        this.objectMapper = objectMapper;
        this.configuredApiKey = configuredApiKey;
    }

    public void setConfiguredApiKey(String configuredApiKey) {
        this.configuredApiKey = configuredApiKey;
    }

    @PostConstruct
    public void validateApiKey() {
        if (configuredApiKey == null || configuredApiKey.trim().isEmpty()) {
            throw new IllegalStateException(
                "CRITICAL SECURITY CONFIGURATION ERROR: INGEST_API_KEY environment variable is missing or blank. " +
                "Please configure INGEST_API_KEY in your environment."
            );
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!isIngestPostRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String providedKey = request.getHeader("X-Ingest-Key");
        if (isValidKey(providedKey)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpServletResponse.SC_UNAUTHORIZED);
        body.put("error", "Unauthorized");
        body.put("message", "Invalid or missing X-Ingest-Key header");
        body.put("path", request.getRequestURI());

        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private boolean isIngestPostRequest(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return "/api/events/ingest".equals(path);
    }

    private boolean isValidKey(String providedKey) {
        if (providedKey == null || configuredApiKey == null || configuredApiKey.trim().isEmpty()) {
            return false;
        }
        byte[] expected = configuredApiKey.getBytes(StandardCharsets.UTF_8);
        byte[] provided = providedKey.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, provided);
    }
}
