package com.disaster.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window rate limiter service.
 * Tracks request timestamps in a configurable 60-second window.
 */
@Service
public class RateLimiterService {

    @Value("${app.rate-limit.enabled:true}")
    private boolean enabled = true;

    @Value("${app.rate-limit.auth.max-requests-per-minute-ip:5}")
    private int authMaxPerMinuteIp = 5;

    @Value("${app.rate-limit.auth.max-requests-per-minute-email:5}")
    private int authMaxPerMinuteEmail = 5;

    @Value("${app.rate-limit.rescue.max-requests-per-minute-ip:5}")
    private int rescueMaxPerMinuteIp = 5;

    private static final long WINDOW_MS = 60_000L;

    private final Map<String, Deque<Long>> requestWindows = new ConcurrentHashMap<>();

    public record RateLimitResult(boolean allowed, long retryAfterSeconds) {}

    public RateLimitResult checkAuth(String ip, String identifier) {
        if (!enabled) {
            return new RateLimitResult(true, 0);
        }

        long now = System.currentTimeMillis();
        long windowStart = now - WINDOW_MS;

        String ipKey = "auth:ip:" + (ip != null ? ip.trim() : "unknown");
        String idKey = (identifier != null && !identifier.isBlank())
                ? "auth:id:" + identifier.toLowerCase().trim()
                : null;

        Deque<Long> ipTimestamps = requestWindows.computeIfAbsent(ipKey, k -> new ArrayDeque<>());
        Deque<Long> idTimestamps = (idKey != null)
                ? requestWindows.computeIfAbsent(idKey, k -> new ArrayDeque<>())
                : null;

        // Check and update atomically
        synchronized (ipTimestamps) {
            while (!ipTimestamps.isEmpty() && ipTimestamps.peekFirst() <= windowStart) {
                ipTimestamps.pollFirst();
            }

            if (ipTimestamps.size() >= authMaxPerMinuteIp) {
                long oldest = ipTimestamps.peekFirst();
                long retryAfterMs = (oldest + WINDOW_MS) - now;
                return new RateLimitResult(false, Math.max(1, (retryAfterMs + 999) / 1000));
            }

            if (idTimestamps != null) {
                synchronized (idTimestamps) {
                    while (!idTimestamps.isEmpty() && idTimestamps.peekFirst() <= windowStart) {
                        idTimestamps.pollFirst();
                    }

                    if (idTimestamps.size() >= authMaxPerMinuteEmail) {
                        long oldest = idTimestamps.peekFirst();
                        long retryAfterMs = (oldest + WINDOW_MS) - now;
                        return new RateLimitResult(false, Math.max(1, (retryAfterMs + 999) / 1000));
                    }

                    ipTimestamps.addLast(now);
                    idTimestamps.addLast(now);
                    return new RateLimitResult(true, 0);
                }
            } else {
                ipTimestamps.addLast(now);
                return new RateLimitResult(true, 0);
            }
        }
    }

    public RateLimitResult checkRescue(String ip) {
        if (!enabled) {
            return new RateLimitResult(true, 0);
        }

        long now = System.currentTimeMillis();
        long windowStart = now - WINDOW_MS;
        String key = "rescue:ip:" + (ip != null ? ip.trim() : "unknown");

        Deque<Long> timestamps = requestWindows.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= windowStart) {
                timestamps.pollFirst();
            }

            if (timestamps.size() >= rescueMaxPerMinuteIp) {
                long oldest = timestamps.peekFirst();
                long retryAfterMs = (oldest + WINDOW_MS) - now;
                return new RateLimitResult(false, Math.max(1, (retryAfterMs + 999) / 1000));
            }

            timestamps.addLast(now);
            return new RateLimitResult(true, 0);
        }
    }

    public void clear() {
        requestWindows.clear();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setAuthMaxPerMinuteIp(int authMaxPerMinuteIp) {
        this.authMaxPerMinuteIp = authMaxPerMinuteIp;
    }

    public void setAuthMaxPerMinuteEmail(int authMaxPerMinuteEmail) {
        this.authMaxPerMinuteEmail = authMaxPerMinuteEmail;
    }

    public void setRescueMaxPerMinuteIp(int rescueMaxPerMinuteIp) {
        this.rescueMaxPerMinuteIp = rescueMaxPerMinuteIp;
    }
}

