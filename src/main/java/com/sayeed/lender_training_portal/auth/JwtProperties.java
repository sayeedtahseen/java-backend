package com.sayeed.lender_training_portal.auth;

import java.time.Duration;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties ("app.jwt")
public record JwtProperties(String issuer, Duration ttl, String secret) {
    
    public JwtProperties {
        if (secret == null || Base64.getDecoder().decode(secret).length < 32) {
            throw new IllegalStateException("app.jwt.secret must be a Base64 value of at least 32 bytes");
        }
    }

    public SecretKey secretKey() {
        return new SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA256");
    }
    
}
