package com.disaster.util;

import java.util.Set;

public class PasswordValidator {

    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "87654321", "987654321", "0987654321", "password", "password123",
            "adminpassword", "qwerty123", "welcome123", "letmein123", "pass1234",
            "iloveyou", "monkey123", "dragon123", "master123", "sunshine"
    );

    public static void validate(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long.");
        }
        if (password.contains(" ")) {
            throw new IllegalArgumentException("Password cannot contain spaces.");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new IllegalArgumentException("Password must contain at least one uppercase letter (A–Z).");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new IllegalArgumentException("Password must contain at least one lowercase letter (a–z).");
        }
        if (!password.matches(".*[0-9].*")) {
            throw new IllegalArgumentException("Password must contain at least one digit (0–9).");
        }
        if (!password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*")) {
            throw new IllegalArgumentException("Password must contain at least one special character (@, #, $, !, %, etc.).");
        }
        if (COMMON_PASSWORDS.contains(password.toLowerCase().trim())) {
            throw new IllegalArgumentException("This password is too common (e.g. password123). Please choose a stronger password.");
        }
    }
}
