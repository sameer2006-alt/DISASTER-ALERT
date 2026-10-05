package com.disaster.service;

import com.disaster.model.OtpVerification;
import com.disaster.repository.OtpVerificationRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class OtpService {

    private final OtpVerificationRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, List<Instant>> sendHistoryByEmail = new ConcurrentHashMap<>();
    private Clock clock = Clock.systemUTC();

    public OtpService(OtpVerificationRepository otpRepository, PasswordEncoder passwordEncoder) {
        this.otpRepository = otpRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public String generateAndStore(String email, OtpVerification.OtpPurpose purpose, String payloadJson) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be empty");
        }

        checkRateLimits(email);

        // Delete any existing OTP for this specific email and purpose
        otpRepository.deleteByEmailAndPurpose(email, purpose);

        Instant now = clock.instant();

        // Generate 6-digit numeric OTP (100000 to 999999)
        int code = random.nextInt(900000) + 100000;
        String otp = String.valueOf(code);

        String otpHash = passwordEncoder.encode(otp);

        OtpVerification record = OtpVerification.builder()
                .email(email)
                .otpHash(otpHash)
                .expiresAt(now.plus(5, ChronoUnit.MINUTES))
                .purpose(purpose)
                .payloadJson(payloadJson)
                .attempts(0)
                .createdAt(now)
                .build();

        otpRepository.save(record);
        recordSend(email, now);
        return otp;
    }

    public OtpVerification verify(String email, OtpVerification.OtpPurpose purpose, String otp) {
        if (email == null || otp == null || otp.isBlank()) {
            throw new IllegalArgumentException("Invalid or expired code");
        }

        Optional<OtpVerification> opt = (purpose != null)
                ? otpRepository.findByEmailAndPurpose(email, purpose)
                : otpRepository.findByEmail(email);

        if (opt.isEmpty()) {
            throw new IllegalArgumentException("Invalid or expired code");
        }

        OtpVerification record = opt.get();
        Instant now = clock.instant();

        if (record.getExpiresAt() != null && record.getExpiresAt().isBefore(now)) {
            otpRepository.delete(record);
            throw new IllegalArgumentException("Invalid or expired code");
        }

        boolean matches = passwordEncoder.matches(otp, record.getOtpHash());
        if (!matches) {
            int attempts = record.getAttempts() + 1;
            record.setAttempts(attempts);
            if (attempts >= 5) {
                // Lockout: purge the record after 5 wrong attempts; user must resend
                otpRepository.delete(record);
            } else {
                otpRepository.save(record);
            }
            throw new IllegalArgumentException("Invalid or expired code");
        }

        // Single-use: delete immediately upon successful verification so code cannot be reused
        otpRepository.delete(record);
        return record;
    }

    public OtpVerification verify(String email, String otp) {
        return verify(email, null, otp);
    }

    public void deleteByEmail(String email) {
        otpRepository.deleteByEmail(email);
    }

    public void deleteByEmailAndPurpose(String email, OtpVerification.OtpPurpose purpose) {
        otpRepository.deleteByEmailAndPurpose(email, purpose);
    }

    private void checkRateLimits(String email) {
        Instant now = clock.instant();
        List<Instant> history = sendHistoryByEmail.computeIfAbsent(email, k -> new CopyOnWriteArrayList<>());

        // Prune timestamps older than 1 hour
        history.removeIf(t -> t.isBefore(now.minus(1, ChronoUnit.HOURS)));

        if (!history.isEmpty()) {
            Instant lastSend = history.get(history.size() - 1);
            long secondsSinceLast = ChronoUnit.SECONDS.between(lastSend, now);
            if (secondsSinceLast < 60) {
                long retryAfter = 60 - secondsSinceLast;
                throw new com.disaster.exception.RateLimitException(
                        "Please wait at least 60 seconds before requesting a new code", retryAfter);
            }
        }

        if (history.size() >= 5) {
            long secondsSinceOldest = ChronoUnit.SECONDS.between(history.get(0), now);
            long retryAfter = Math.max(1, 3600 - secondsSinceOldest);
            throw new com.disaster.exception.RateLimitException(
                    "Maximum 5 OTP requests per hour exceeded. Please try again later", retryAfter);
        }
    }

    private void recordSend(String email, Instant sendTime) {
        List<Instant> history = sendHistoryByEmail.computeIfAbsent(email, k -> new CopyOnWriteArrayList<>());
        history.add(sendTime);
    }

    public void clearRateLimits() {
        sendHistoryByEmail.clear();
    }

    public void setClock(Clock clock) {
        this.clock = clock;
    }
}
