package com.disaster.service;

import com.disaster.config.JwtService;
import com.disaster.dto.AuthResponse;
import com.disaster.model.Organisation;
import com.disaster.model.OtpVerification;
import com.disaster.repository.OrganisationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
public class OrganisationAuthService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OrganisationAuthService.class);

    private final OrganisationRepository organisationRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final EmailService emailService;
    private final GeoLocationLookupService geoLocationLookupService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OrganisationAuthService(
            OrganisationRepository organisationRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            OtpService otpService,
            EmailService emailService,
            GeoLocationLookupService geoLocationLookupService) {
        this.organisationRepository = organisationRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.otpService = otpService;
        this.emailService = emailService;
        this.geoLocationLookupService = geoLocationLookupService;
    }

    public AuthResponse register(OrgRegistrationData data) {
        Organisation existingOrgByEmail = organisationRepository.findByEmail(data.email()).orElse(null);
        if (existingOrgByEmail != null) {
            if (existingOrgByEmail.isVerified()) {
                throw new com.disaster.exception.ConflictException("Email already registered. Please log in.");
            }
            organisationRepository.delete(existingOrgByEmail);
        }

        double lat;
        double lon;
        if (data.latitude() != null && data.longitude() != null) {
            lat = data.latitude();
            lon = data.longitude();
        } else {
            var coordsOpt = geoLocationLookupService.lookup(data.city(), data.state());
            if (coordsOpt.isPresent()) {
                lat = coordsOpt.get().latitude();
                lon = coordsOpt.get().longitude();
            } else {
                log.warn("Unknown location for organisation registration: city='{}', state='{}'. Falling back to India centre.",
                        data.city(), data.state());
                lat = GeoLocationLookupService.INDIA_CENTRE.latitude();
                lon = GeoLocationLookupService.INDIA_CENTRE.longitude();
            }
        }

        Organisation org = Organisation.builder()
                .organisationName(data.organisationName())
                .email(data.email())
                .password(passwordEncoder.encode(data.password()))
                .country(data.country())
                .state(data.state())
                .city(data.city())
                .headquartersLocation(data.headquartersLocation())
                .verified(false)
                .activeStatus(true)
                .latitude(lat)
                .longitude(lon)
                .createdAt(Instant.now())
                .build();
        org.syncGeo();
        organisationRepository.save(org);

        String otp;
        try {
            otp = otpService.generateAndStore(data.email(), OtpVerification.OtpPurpose.ORG_REGISTER, null);
            emailService.sendOtpEmail(data.email(), otp);
        } catch (Exception e) {
            organisationRepository.delete(org);
            throw new RuntimeException("Failed to prepare verification email: " + e.getMessage(), e);
        }

        return AuthResponse.builder()
                .requireOtp(true)
                .email(data.email())
                .message("Verification OTP sent to " + data.email() + ". Please check your Gmail.")
                .build();
    }

    /** Second step organisation registration — verify OTP and activate organisation */
    public AuthResponse verifyRegisterOtp(String email, String otp) {
        OtpVerification verification = otpService.verify(email, OtpVerification.OtpPurpose.ORG_REGISTER, otp);
        if (verification.getPurpose() != OtpVerification.OtpPurpose.ORG_REGISTER) {
            throw new IllegalArgumentException("Invalid or expired code");
        }

        Organisation org = organisationRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired code"));

        org.setVerified(true);
        organisationRepository.save(org);

        String token = jwtService.generateToken(org.getEmail(),
                Map.of("role", "ROLE_ORGANISATION", "orgId", org.getId()));
        return new AuthResponse(token, "ROLE_ORGANISATION", org.getOrganisationName(), "Organisation verified and registered");
    }

    /** Organisation login validation — check credentials directly and return JWT (NO OTP flow) */
    public AuthResponse login(String email, String password) {
        Organisation org = organisationRepository.findByEmail(email)
                .orElseThrow(() -> new org.springframework.security.authentication.BadCredentialsException("Invalid credentials"));
        if (!passwordEncoder.matches(password, org.getPassword())) {
            throw new org.springframework.security.authentication.BadCredentialsException("Invalid credentials");
        }

        if (!org.isVerified()) {
            throw new com.disaster.exception.AccountNotVerifiedException("Account not verified", org.getEmail());
        }

        String token = jwtService.generateToken(org.getEmail(),
                Map.of("role", "ROLE_ORGANISATION", "orgId", org.getId()));
        return new AuthResponse(token, "ROLE_ORGANISATION", org.getOrganisationName(), "Login successful");
    }

    /** Resend organisation registration verification OTP */
    public AuthResponse resendOtp(String email) {
        Organisation org = organisationRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Organisation not found"));

        if (org.isVerified()) {
            throw new com.disaster.exception.ConflictException("Account already verified");
        }

        String newOtp = otpService.generateAndStore(email, OtpVerification.OtpPurpose.ORG_REGISTER, null);
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

    /** Verify Organisation Login 2FA OTP (Deprecated/No-op stub) */
    public AuthResponse verifyLoginOtp(String email, String otp) {
        throw new UnsupportedOperationException("Login 2FA OTP is deprecated");
    }

    public Organisation updateProfile(String email, com.disaster.dto.OrgProfileUpdateRequest updates) {
        Organisation org = organisationRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Organisation not found"));
        if (updates.organisationName() != null && !updates.organisationName().isBlank()) org.setOrganisationName(updates.organisationName());
        if (updates.description() != null) org.setDescription(updates.description());
        if (updates.logoUrl() != null) org.setLogoUrl(updates.logoUrl());
        if (updates.country() != null) org.setCountry(updates.country());
        if (updates.state() != null) org.setState(updates.state());
        if (updates.city() != null) org.setCity(updates.city());
        if (updates.headquartersLocation() != null) org.setHeadquartersLocation(updates.headquartersLocation());
        if (updates.operatingLocations() != null) org.setOperatingLocations(updates.operatingLocations());
        if (updates.supportTypes() != null) org.setSupportTypes(updates.supportTypes());
        if (updates.resourcesAvailable() != null) org.setResourcesAvailable(updates.resourcesAvailable());
        if (updates.shelterCapacity() != null && updates.shelterCapacity() >= 0) org.setShelterCapacity(updates.shelterCapacity());
        if (updates.foodCapacity() != null && updates.foodCapacity() >= 0) org.setFoodCapacity(updates.foodCapacity());
        if (updates.medicalCapacity() != null && updates.medicalCapacity() >= 0) org.setMedicalCapacity(updates.medicalCapacity());
        if (updates.activeStatus() != null) org.setActiveStatus(updates.activeStatus());
        if (updates.latitude() != null) org.setLatitude(updates.latitude());
        if (updates.longitude() != null) org.setLongitude(updates.longitude());
        if (updates.contactNumber() != null) org.setContactNumber(updates.contactNumber());
        if (updates.website() != null) org.setWebsite(updates.website());
        org.syncGeo();
        return organisationRepository.save(org);
    }

    public record OrgRegistrationData(
            String organisationName, String email, String password,
            String country, String state, String city, String headquartersLocation,
            Double latitude, Double longitude) {
        public OrgRegistrationData(String organisationName, String email, String password,
                                   String country, String state, String city, String headquartersLocation) {
            this(organisationName, email, password, country, state, city, headquartersLocation, null, null);
        }
    }
}
