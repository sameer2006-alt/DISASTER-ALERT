package com.disaster.exception;

public class RateLimitException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}

