package com.disaster.controller;

import com.disaster.dto.AuthResponse;
import com.disaster.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        if (!req.password().equals(req.confirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }
        com.disaster.util.PasswordValidator.validate(req.password());
        var data = new AuthService.UserRegistrationData(
                req.username(), req.email(), req.password(),
                req.location(), req.state(), req.city(),
                req.latitude(), req.longitude());
        return ResponseEntity.ok(authService.register(data));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<AuthResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest req) {
        return ResponseEntity.ok(authService.verifyRegisterOtp(req.email(), req.otp()));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req.username(), req.password()));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<AuthResponse> resendOtp(@RequestBody java.util.Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        return ResponseEntity.ok(authService.resendOtp(email));
    }

    public record RegisterRequest(
            @NotBlank String username,
            @Email String email,
            @NotBlank String password,
            @NotBlank String confirmPassword,
            String location,
            String state,
            String city,
            @DecimalMin(value = "-90.0", message = "latitude must be between -90 and 90")
            @DecimalMax(value = "90.0", message = "latitude must be between -90 and 90")
            Double latitude,
            @DecimalMin(value = "-180.0", message = "longitude must be between -180 and 180")
            @DecimalMax(value = "180.0", message = "longitude must be between -180 and 180")
            Double longitude) {}

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record VerifyOtpRequest(@NotBlank @Email String email, @NotBlank String otp) {}
}
