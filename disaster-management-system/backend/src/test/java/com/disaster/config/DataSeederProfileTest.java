package com.disaster.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DataSeederProfileTest {

    @Test
    @DisplayName("DataSeeder must be annotated with @Profile('dev') so it is omitted in production")
    void testDataSeederHasDevProfileAnnotation() {
        Profile profileAnnotation = DataSeeder.class.getAnnotation(Profile.class);
        assertNotNull(profileAnnotation, "DataSeeder must have @Profile annotation");
        assertArrayEquals(new String[]{"dev"}, profileAnnotation.value(), "DataSeeder must only run in 'dev' profile");
    }
}

