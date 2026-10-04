package com.disaster.service;

import com.disaster.config.JwtService;
import com.disaster.dto.AuthResponse;
import com.disaster.model.OtpVerification;
import com.disaster.model.User;
import com.disaster.model.UserRole;
import com.disaster.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final EmailService emailService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuthService(
            UserRepository userRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            OtpService otpService,
            EmailService emailService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.otpService = otpService;
        this.emailService = emailService;
    }

    public AuthResponse register(UserRegistrationData data) {
        User existingUserByEmail = userRepository.findByEmail(data.email()).orElse(null);
        if (existingUserByEmail != null) {
            if (existingUserByEmail.isVerified()) {
                throw new com.disaster.exception.ConflictException("Email already registered. Please log in.");
            }
            // Clear unverified registration attempt so user can re-register cleanly
            userRepository.delete(existingUserByEmail);
        }

        User existingUserByUsername = userRepository.findByUsername(data.username()).orElse(null);
        if (existingUserByUsername != null) {
            if (existingUserByUsername.isVerified()) {
                throw new com.disaster.exception.ConflictException("Username is already taken by a verified user.");
            }
            userRepository.delete(existingUserByUsername);
        }

        User user = User.builder()
                .username(data.username())
                .email(data.email())
                .password(passwordEncoder.encode(data.password()))
                .location(data.location())
                .state(data.state())
                .city(data.city())
                .role(UserRole.CITIZEN)
                .verified(false)
                .latitude(20.5937)
                .longitude(78.9629)
                .createdAt(Instant.now())
                .build();
        user.syncGeo();
        userRepository.save(user);

        String otp;
        try {
            otp = otpService.generateAndStore(data.email(), OtpVerification.OtpPurpose.USER_REGISTER, null);
            emailService.sendOtpEmail(data.email(), otp);
        } catch (Exception e) {
            userRepository.delete(user);
            throw new RuntimeException("Failed to prepare verification email: " + e.getMessage(), e);
        }

        return AuthResponse.builder()
                .requireOtp(true)
                .email(data.email())
                .message("Verification OTP sent to " + data.email() + ". Please check your Gmail.")
                .build();
    }

    /** Second step registration — verify OTP and activate user in DB */
    public AuthResponse verifyRegisterOtp(String email, String otp) {
        OtpVerification verification = otpService.verify(email, OtpVerification.OtpPurpose.USER_REGISTER, otp);
        if (verification.getPurpose() != OtpVerification.OtpPurpose.USER_REGISTER) {
            throw new IllegalArgumentException("Invalid or expired code");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired code"));

        user.setVerified(true);
        userRepository.save(user);

        String token = jwtService.generateToken(user.getUsername(),
                Map.of("role", user.getRole().name(), "userId", user.getId()));
        return new AuthResponse(token, user.getRole().name(), user.getUsername(), "Email verified successfully. You can now login.");
    }

    /** Login validation — check credentials directly and return JWT (NO OTP flow) */
    public AuthResponse login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseGet(() -> userRepository.findByEmail(username)
                        .orElseThrow(() -> new IllegalArgumentException("Invalid credentials")));
        
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        if (!user.isVerified()) {
            throw new com.disaster.exception.AccountNotVerifiedException("Account not verified", user.getEmail());
        }

        String token = jwtService.generateToken(user.getUsername(),
                Map.of("role", user.getRole().name(), "userId", user.getId()));
        return new AuthResponse(token, user.getRole().name(), user.getUsername(), "Login successful");
    }

    /** Resend registration verification OTP */
    public AuthResponse resendOtp(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.isVerified()) {
            throw new com.disaster.exception.ConflictException("Account already verified");
        }

        String newOtp = otpService.generateAndStore(email, OtpVerification.OtpPurpose.USER_REGISTER, null);
        try {
            emailService.sendOtpEmail(email, newOtp);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send OTP: " + e.getMessage(), e);
        }

        return AuthResponse.builder()
                .requireOtp(true)
                .email(email)
                .message("New OTP sent to " + email)
                .build();
    }

    /** Verify Login 2FA OTP (Deprecated/No-op stub) */
    public AuthResponse verifyLoginOtp(String email, String otp) {
        throw new UnsupportedOperationException("Login 2FA OTP is deprecated");
    }

    public record UserRegistrationData(
            String username, String email, String password,
            String location, String state, String city) {}
}
