package com.yang.lblogserver.auth.security.util;

import com.yang.lblogserver.site.service.SiteConfigCacheService;
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

    private static final long DEFAULT_ACCESS_TTL = 7200;
    private static final long DEFAULT_REFRESH_TTL = 604800;

    private final SiteConfigCacheService configService;

    @Value("${lblog.token.token-byte-size:32}")
    private int tokenByteSize;

    private final SecureRandom secureRandom = new SecureRandom();

    public TokenGenerator(SiteConfigCacheService configService) {
        this.configService = configService;
    }

    public TokenPairRaw generate() {
        String rawAccessToken = generateRandomToken();
        String rawRefreshToken = generateRandomToken();

        String accessHash = hash(rawAccessToken);
        String refreshHash = hash(rawRefreshToken);

        long accessTtl = getConfigLong("token_access_ttl", DEFAULT_ACCESS_TTL);
        long refreshTtl = getConfigLong("token_refresh_ttl", DEFAULT_REFRESH_TTL);

        LocalDateTime accessExpiresAt = LocalDateTime.now().plusSeconds(accessTtl);
        LocalDateTime refreshExpiresAt = LocalDateTime.now().plusSeconds(refreshTtl);

        return new TokenPairRaw()
                .setRawAccessToken(rawAccessToken)
                .setRawRefreshToken(rawRefreshToken)
                .setAccessHash(accessHash)
                .setRefreshHash(refreshHash)
                .setAccessExpiresAt(accessExpiresAt)
                .setRefreshExpiresAt(refreshExpiresAt);
    }

    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(encodedHash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private String generateRandomToken() {
        byte[] randomBytes = new byte[tokenByteSize];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            hexString.append(String.format("%02x", b & 0xff));
        }
        return hexString.toString();
    }

    private long getConfigLong(String key, long defaultValue) {
        String val = configService.getConfigValue(key);
        if (val != null) {
            try { return Long.parseLong(val); }
            catch (NumberFormatException e) { /* fall through */ }
        }
        return defaultValue;
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
