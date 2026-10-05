package com.disaster.service;

import com.disaster.model.OtpVerification;
import com.disaster.repository.OtpVerificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock
    private OtpVerificationRepository otpRepository;

    private PasswordEncoder passwordEncoder;
    private OtpService otpService;

    private static final String TEST_EMAIL = "citizen@example.org";
    private static final OtpVerification.OtpPurpose PURPOSE = OtpVerification.OtpPurpose.USER_REGISTER;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        otpService = new OtpService(otpRepository, passwordEncoder);
        otpService.clearRateLimits();
    }

    @Test
    @DisplayName("Correct code passes once and is consumed")
    void testCorrectCodePassesOnce() {
        AtomicReference<OtpVerification> stored = new AtomicReference<>();
        when(otpRepository.save(any(OtpVerification.class))).thenAnswer(invocation -> {
            OtpVerification record = invocation.getArgument(0);
            stored.set(record);
            return record;
        });

        String otp = otpService.generateAndStore(TEST_EMAIL, PURPOSE, null);
        assertNotNull(otp);
        assertEquals(6, otp.length());

        when(otpRepository.findByEmailAndPurpose(TEST_EMAIL, PURPOSE))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));

        OtpVerification verified = otpService.verify(TEST_EMAIL, PURPOSE, otp);
        assertNotNull(verified);
        assertEquals(TEST_EMAIL, verified.getEmail());
        assertEquals(PURPOSE, verified.getPurpose());

        // Verify it was deleted (consumed)
        verify(otpRepository).delete(stored.get());
    }

    @Test
    @DisplayName("Reused code fails after successful verification")
    void testReusedCodeFails() {
        AtomicReference<OtpVerification> stored = new AtomicReference<>();
        when(otpRepository.save(any(OtpVerification.class))).thenAnswer(invocation -> {
            OtpVerification record = invocation.getArgument(0);
            stored.set(record);
            return record;
        });

        doAnswer(invocation -> {
            stored.set(null);
            return null;
        }).when(otpRepository).delete(any(OtpVerification.class));

        when(otpRepository.findByEmailAndPurpose(TEST_EMAIL, PURPOSE))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));

        String otp = otpService.generateAndStore(TEST_EMAIL, PURPOSE, null);

        // First verification succeeds
        OtpVerification verified = otpService.verify(TEST_EMAIL, PURPOSE, otp);
        assertNotNull(verified);

        // Second verification with identical code must fail because OTP was consumed
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                otpService.verify(TEST_EMAIL, PURPOSE, otp));
        assertEquals("Invalid or expired code", ex.getMessage());
    }

    @Test
    @DisplayName("6th wrong attempt locks out and purges OTP record")
    void testSixthWrongAttemptLocks() {
        AtomicReference<OtpVerification> stored = new AtomicReference<>();
        when(otpRepository.save(any(OtpVerification.class))).thenAnswer(invocation -> {
            OtpVerification record = invocation.getArgument(0);
            stored.set(record);
            return record;
        });

        doAnswer(invocation -> {
            stored.set(null);
            return null;
        }).when(otpRepository).delete(any(OtpVerification.class));

        when(otpRepository.findByEmailAndPurpose(TEST_EMAIL, PURPOSE))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));

        String correctOtp = otpService.generateAndStore(TEST_EMAIL, PURPOSE, null);
        String wrongOtp = "999888";

        // Attempts 1 through 4: increment attempts counter and reject
        for (int i = 1; i <= 4; i++) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    otpService.verify(TEST_EMAIL, PURPOSE, wrongOtp));
            assertEquals("Invalid or expired code", ex.getMessage());
            assertNotNull(stored.get(), "Record should still exist on attempt " + i);
            assertEquals(i, stored.get().getAttempts());
        }

        // 5th wrong attempt: triggers lockout and deletes record
        IllegalArgumentException ex5 = assertThrows(IllegalArgumentException.class, () ->
                otpService.verify(TEST_EMAIL, PURPOSE, wrongOtp));
        assertEquals("Invalid or expired code", ex5.getMessage());
        assertNull(stored.get(), "Record must be deleted after 5 failed attempts");

        // 6th attempt (even with correct code) must fail because record was deleted/locked
        IllegalArgumentException ex6 = assertThrows(IllegalArgumentException.class, () ->
                otpService.verify(TEST_EMAIL, PURPOSE, correctOtp));
        assertEquals("Invalid or expired code", ex6.getMessage());
    }

    @Test
    @DisplayName("Expired code fails and is deleted")
    void testExpiredCodeFails() {
        Instant past = Instant.now().minus(10, ChronoUnit.MINUTES);
        String otp = "654321";
        OtpVerification expiredRecord = OtpVerification.builder()
                .email(TEST_EMAIL)
                .otpHash(passwordEncoder.encode(otp))
                .expiresAt(past)
                .purpose(PURPOSE)
                .attempts(0)
                .build();

        when(otpRepository.findByEmailAndPurpose(TEST_EMAIL, PURPOSE))
                .thenReturn(Optional.of(expiredRecord));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                otpService.verify(TEST_EMAIL, PURPOSE, otp));
        assertEquals("Invalid or expired code", ex.getMessage());
        verify(otpRepository).delete(expiredRecord);
    }

    @Test
    @DisplayName("Magic bypass codes fail both with and without existing record")
    void testMagicCodesFail() {
        AtomicReference<OtpVerification> stored = new AtomicReference<>();
        when(otpRepository.save(any(OtpVerification.class))).thenAnswer(invocation -> {
            OtpVerification record = invocation.getArgument(0);
            stored.set(record);
            return record;
        });

        otpService.generateAndStore(TEST_EMAIL, PURPOSE, null);
        when(otpRepository.findByEmailAndPurpose(TEST_EMAIL, PURPOSE))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));

        // Magic codes must fail when an active record exists
        assertThrows(IllegalArgumentException.class, () ->
                otpService.verify(TEST_EMAIL, PURPOSE, "123456"));
        assertThrows(IllegalArgumentException.class, () ->
                otpService.verify(TEST_EMAIL, PURPOSE, "000000"));

        // Magic codes must fail when NO record exists (no fabrication)
        when(otpRepository.findByEmailAndPurpose("nobody@example.org", PURPOSE))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                otpService.verify("nobody@example.org", PURPOSE, "123456"));
        assertThrows(IllegalArgumentException.class, () ->
                otpService.verify("nobody@example.org", PURPOSE, "000000"));
    }

    @Test
    @DisplayName("Resend cooldown of 60 seconds is enforced")
    void testResendCooldownEnforced() {
        when(otpRepository.save(any(OtpVerification.class))).thenAnswer(inv -> inv.getArgument(0));

        // First send succeeds
        assertNotNull(otpService.generateAndStore(TEST_EMAIL, PURPOSE, null));

        // Immediate second send fails
        com.disaster.exception.RateLimitException ex = assertThrows(com.disaster.exception.RateLimitException.class, () ->
                otpService.generateAndStore(TEST_EMAIL, PURPOSE, null));
        assertTrue(ex.getMessage().contains("Please wait at least 60 seconds"));
        assertTrue(ex.getRetryAfterSeconds() > 0);
    }

    @Test
    @DisplayName("Maximum 5 OTP sends per hour per email is enforced")
    void testMaxSendsPerHourEnforced() {
        when(otpRepository.save(any(OtpVerification.class))).thenAnswer(inv -> inv.getArgument(0));

        AtomicReference<Instant> current = new AtomicReference<>(Instant.parse("2026-10-04T12:00:00Z"));
        otpService.setClock(new Clock() {
            @Override
            public ZoneId getZone() { return ZoneId.of("UTC"); }
            @Override
            public Clock withZone(ZoneId zone) { return this; }
            @Override
            public Instant instant() { return current.get(); }
        });

        // 5 sends spaced by 61 seconds each succeed
        for (int i = 0; i < 5; i++) {
            assertNotNull(otpService.generateAndStore(TEST_EMAIL, PURPOSE, null));
            current.set(current.get().plus(61, ChronoUnit.SECONDS));
        }

        // 6th send within the hour window must be rejected
        com.disaster.exception.RateLimitException ex = assertThrows(com.disaster.exception.RateLimitException.class, () ->
                otpService.generateAndStore(TEST_EMAIL, PURPOSE, null));
        assertTrue(ex.getMessage().contains("Maximum 5 OTP requests per hour exceeded"));
        assertTrue(ex.getRetryAfterSeconds() > 0);
    }

    @Test
    @DisplayName("Purpose isolation keys OTPs by (email, purpose) without cross-overwriting")
    void testPurposeIsolation() {
        when(otpRepository.save(any(OtpVerification.class))).thenAnswer(inv -> inv.getArgument(0));

        otpService.generateAndStore(TEST_EMAIL, OtpVerification.OtpPurpose.USER_REGISTER, null);
        verify(otpRepository).deleteByEmailAndPurpose(TEST_EMAIL, OtpVerification.OtpPurpose.USER_REGISTER);

        // Clear rate limits to test purpose deletion
        otpService.clearRateLimits();
        otpService.generateAndStore(TEST_EMAIL, OtpVerification.OtpPurpose.ORG_REGISTER, null);
        verify(otpRepository).deleteByEmailAndPurpose(TEST_EMAIL, OtpVerification.OtpPurpose.ORG_REGISTER);

        // Broad deleteByEmail must NOT have been called
        verify(otpRepository, never()).deleteByEmail(anyString());
    }
}

