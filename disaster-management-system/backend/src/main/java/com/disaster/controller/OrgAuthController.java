package com.disaster.controller;

import com.disaster.dto.AuthResponse;
import com.disaster.model.Organisation;
import com.disaster.service.OrganisationAuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/org")
public class OrgAuthController {

    private final OrganisationAuthService orgAuthService;

    public OrgAuthController(OrganisationAuthService orgAuthService) {
        this.orgAuthService = orgAuthService;
    }

    @PostMapping("/auth/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody OrgRegisterRequest req) {
        if (!req.password().equals(req.confirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }
        com.disaster.util.PasswordValidator.validate(req.password());
        var data = new OrganisationAuthService.OrgRegistrationData(
                req.organisationName(), req.email(), req.password(),
                req.country(), req.state(), req.city(), req.headquartersLocation());
        return ResponseEntity.ok(orgAuthService.register(data));
    }

    @PostMapping("/auth/verify-otp")
    public ResponseEntity<AuthResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest req) {
        return ResponseEntity.ok(orgAuthService.verifyRegisterOtp(req.email(), req.otp()));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<OrgLoginResponse> login(@RequestBody OrgLoginRequest req) {
        // Map AuthResponse to matching legacy or return directly
        AuthResponse response = orgAuthService.login(req.email(), req.password());
        return ResponseEntity.ok(new OrgLoginResponse(
                response.getToken(),
                response.getRole(),
                response.getUsername(),
                response.getMessage(),
                response.isRequireOtp(),
                response.getEmail()
        ));
    }

    @PostMapping("/auth/resend-otp")
    public ResponseEntity<AuthResponse> resendOtp(@RequestBody java.util.Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        return ResponseEntity.ok(orgAuthService.resendOtp(email));
    }

    @PutMapping("/profile")
    public ResponseEntity<Organisation> updateProfile(
            Authentication auth,
            @Valid @RequestBody com.disaster.dto.OrgProfileUpdateRequest updates) {
        return ResponseEntity.ok(orgAuthService.updateProfile(auth.getName(), updates));
    }

    public record OrgRegisterRequest(
            @NotBlank String organisationName,
            @Email String email,
            @NotBlank String password,
            @NotBlank String confirmPassword,
            String country, String state, String city, String headquartersLocation) {}

    public record OrgLoginRequest(@Email String email, @NotBlank String password) {}

    public record VerifyOtpRequest(@NotBlank @Email String email, @NotBlank String otp) {}

    public record OrgLoginResponse(
            String token,
            String role,
            String username,
            String message,
            boolean requireOtp,
            String email) {}
}
