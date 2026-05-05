package com.yang.lblogserver.security.util;

import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Component
public class TokenGenerator {

    @Value("${lblog.token.access-token-expire-minutes:120}")
    private long accessTokenExpireMinutes;

    @Value("${lblog.token.refresh-token-expire-days:7}")
    private long refreshTokenExpireDays;

    @Value("${lblog.token.token-byte-size:32}")
    private int tokenByteSize;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generate a pair of access and refresh tokens with their hashes and expiration times.
     */
    public TokenPairRaw generate() {
        String rawAccessToken = generateRandomToken();
        String rawRefreshToken = generateRandomToken();

        String accessHash = hash(rawAccessToken);
        String refreshHash = hash(rawRefreshToken);

        LocalDateTime accessExpiresAt = LocalDateTime.now().plusMinutes(accessTokenExpireMinutes);
        LocalDateTime refreshExpiresAt = LocalDateTime.now().plusDays(refreshTokenExpireDays);

        return new TokenPairRaw()
                .setRawAccessToken(rawAccessToken)
                .setRawRefreshToken(rawRefreshToken)
                .setAccessHash(accessHash)
                .setRefreshHash(refreshHash)
                .setAccessExpiresAt(accessExpiresAt)
                .setRefreshExpiresAt(refreshExpiresAt);
    }

    /**
     * Compute SHA-256 hex digest for a raw token string.
     */
    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(encodedHash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Generate a random token string: tokenByteSize random bytes, Base64 URL-safe encoded (no padding).
     */
    private String generateRandomToken() {
        byte[] randomBytes = new byte[tokenByteSize];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * Convert a byte array to a lowercase hex string.
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            hexString.append(String.format("%02x", b & 0xff));
        }
        return hexString.toString();
    }

    @Data
    @Accessors(chain = true)
    public static class TokenPairRaw {
        private String rawAccessToken;
        private String rawRefreshToken;
        private String accessHash;
        private String refreshHash;
        private LocalDateTime accessExpiresAt;
        private LocalDateTime refreshExpiresAt;
    }
}
