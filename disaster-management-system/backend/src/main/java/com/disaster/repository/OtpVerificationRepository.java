package com.disaster.repository;

import com.disaster.model.OtpVerification;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface OtpVerificationRepository extends MongoRepository<OtpVerification, String> {
    Optional<OtpVerification> findByEmailAndPurpose(String email, OtpVerification.OtpPurpose purpose);
    Optional<OtpVerification> findByEmail(String email);
    void deleteByEmailAndPurpose(String email, OtpVerification.OtpPurpose purpose);
    void deleteByEmail(String email);
}
