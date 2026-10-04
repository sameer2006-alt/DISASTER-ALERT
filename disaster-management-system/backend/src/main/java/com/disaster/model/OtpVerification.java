package com.disaster.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "otp_verifications")
@CompoundIndex(name = "email_purpose_idx", def = "{'email': 1, 'purpose': 1}")
public class OtpVerification {
    @Id
    private String id;
    private String email;
    private String otpHash;

    @Indexed(expireAfterSeconds = 0)
    private Instant expiresAt;

    private OtpPurpose purpose;
    private String payloadJson;

    @Builder.Default
    private int attempts = 0;

    private Instant createdAt;

    public enum OtpPurpose {
        USER_REGISTER, ORG_REGISTER, USER_LOGIN, ORG_LOGIN
    }
}
