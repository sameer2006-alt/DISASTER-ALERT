package com.disaster.dto;

public record UserLocationResponse(
        String message,
        double latitude,
        double longitude,
        String city,
        String state,
        String location
) {}
