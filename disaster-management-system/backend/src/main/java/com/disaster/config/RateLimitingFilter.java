package com.disaster.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;

    public RateLimitingFilter(RateLimiterService rateLimiterService, ObjectMapper objectMapper) {
        this.rateLimiterService = rateLimiterService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        String clientIp = getClientIp(request);

        if (isAuthEndpoint(request, path)) {
            CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);
            String identifier = extractIdentifier(wrappedRequest.getCachedBody());

            RateLimiterService.RateLimitResult result = rateLimiterService.checkAuth(clientIp, identifier);
            if (!result.allowed()) {
                sendRateLimitResponse(request, response, result.retryAfterSeconds());
                return;
            }

            filterChain.doFilter(wrappedRequest, response);
            return;
        }

        if (isRescueEndpoint(request, path)) {
            RateLimiterService.RateLimitResult result = rateLimiterService.checkRescue(clientIp);
            if (!result.allowed()) {
                sendRateLimitResponse(request, response, result.retryAfterSeconds());
                return;
            }

            filterChain.doFilter(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAuthEndpoint(HttpServletRequest request, String path) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        return path.equals("/api/auth/login") ||
                path.equals("/api/auth/register") ||
                path.equals("/api/auth/resend-otp") ||
                path.equals("/api/org/auth/login") ||
                path.equals("/api/org/login") ||
                path.equals("/api/org/auth/register") ||
                path.equals("/api/org/register") ||
                path.equals("/api/org/auth/resend-otp") ||
                path.equals("/api/org/resend-otp");
    }

    private boolean isRescueEndpoint(HttpServletRequest request, String path) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        return path.equals("/api/rescue/request");
    }

    private String extractIdentifier(byte[] bodyBytes) {
        if (bodyBytes == null || bodyBytes.length == 0) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(bodyBytes);
            if (root.hasNonNull("email")) {
                return root.get("email").asText();
            }
            if (root.hasNonNull("username")) {
                return root.get("username").asText();
            }
        } catch (Exception ignored) {
            // Unparseable JSON will be handled by the controller's validation
        }
        return null;
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }

    private void sendRateLimitResponse(HttpServletRequest request, HttpServletResponse response, long retryAfterSeconds)
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        body.put("error", "Too Many Requests");
        body.put("message", "Too many requests. Please try again in " + retryAfterSeconds + " seconds.");
        body.put("retryAfterSeconds", retryAfterSeconds);
        body.put("path", request.getRequestURI());

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}

