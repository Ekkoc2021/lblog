package com.yang.lblogserver.service.impl;

import com.yang.lblogserver.domain.Users;
import com.yang.lblogserver.mapper.UsersMapper;
import com.yang.lblogserver.security.model.LoginUser;
import com.yang.lblogserver.security.model.TokenRecord;
import com.yang.lblogserver.security.repository.TokenRepository;
import com.yang.lblogserver.security.util.TokenGenerator;
import com.yang.lblogserver.service.TokenService;
import com.yang.lblogserver.vo.response.TokenPairVO;
import com.yang.lblogserver.vo.response.UserInfoVO;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenServiceImpl implements TokenService {

    private final TokenGenerator tokenGenerator;
    private final TokenRepository tokenRepository;
    private final UsersMapper usersMapper;

    public TokenServiceImpl(TokenGenerator tokenGenerator, TokenRepository tokenRepository, UsersMapper usersMapper) {
        this.tokenGenerator = tokenGenerator;
        this.tokenRepository = tokenRepository;
        this.usersMapper = usersMapper;
    }

    @Override
    public TokenPairVO issueTokenPair(Long userId) {
        // 1. Load user from UsersMapper
        Users user = usersMapper.selectBatchIds(List.of(userId))
                .stream().findFirst().orElse(null);
        if (user == null) {
            return null;
        }

        // 2. Generate token pair
        TokenGenerator.TokenPairRaw pair = tokenGenerator.generate();

        // 3. Save access TokenRecord
        TokenRecord accessRecord = new TokenRecord();
        accessRecord.setUserId(userId);
        accessRecord.setTokenHash(pair.getAccessHash());
        accessRecord.setTokenType("ACCESS");
        accessRecord.setExpiresAt(pair.getAccessExpiresAt());
        accessRecord.setCreatedAt(LocalDateTime.now());
        accessRecord.setRevoked(false);
        tokenRepository.save(accessRecord);

        // 4. Save refresh TokenRecord
        TokenRecord refreshRecord = new TokenRecord();
        refreshRecord.setUserId(userId);
        refreshRecord.setTokenHash(pair.getRefreshHash());
        refreshRecord.setTokenType("REFRESH");
        refreshRecord.setExpiresAt(pair.getRefreshExpiresAt());
        refreshRecord.setCreatedAt(LocalDateTime.now());
        refreshRecord.setRevoked(false);
        tokenRepository.save(refreshRecord);

        // 5. Calculate expiresIn as seconds between accessExpiresAt and now
        long expiresIn = Duration.between(LocalDateTime.now(), pair.getAccessExpiresAt()).getSeconds();

        // 6. Build UserInfoVO from Users entity
        UserInfoVO userInfo = new UserInfoVO();
        userInfo.setId(user.getId());
        userInfo.setUsername(user.getUsername());
        userInfo.setNickname(user.getNickname());
        userInfo.setAvatar(user.getAvatar());
        userInfo.setEmail(user.getEmail());
        userInfo.setRole(user.getRole());

        // 7. Return TokenPairVO
        return new TokenPairVO(pair.getRawAccessToken(), pair.getRawRefreshToken(), expiresIn, userInfo);
    }

    @Override
    public LoginUser validateAccessToken(String rawToken) {
        // 1. Hash the raw token
        String hash = tokenGenerator.hash(rawToken);

        // 2. Find by hash
        TokenRecord record = tokenRepository.findByHash(hash).orElse(null);
        if (record == null) {
            return null;
        }

        // 3. Check if revoked
        if (record.isRevoked()) {
            return null;
        }

        // 4. Check if expired
        if (record.getExpiresAt() != null && record.getExpiresAt().isBefore(LocalDateTime.now())) {
            return null;
        }

        // 5. Check token type
        if (!"ACCESS".equals(record.getTokenType())) {
            return null;
        }

        // 6. Load user
        Users user = usersMapper.selectBatchIds(List.of(record.getUserId()))
                .stream().findFirst().orElse(null);
        if (user == null) {
            return null;
        }

        // 7. Build and return LoginUser
        return new LoginUser(user);
    }

    @Override
    public Long validateRefreshToken(String rawToken) {
        // 1. Hash the raw token
        String hash = tokenGenerator.hash(rawToken);

        // 2. Find by hash
        TokenRecord record = tokenRepository.findByHash(hash).orElse(null);
        if (record == null) {
            return null;
        }

        // 3. Check if revoked
        if (record.isRevoked()) {
            return null;
        }

        // 4. Check if expired
        if (record.getExpiresAt() != null && record.getExpiresAt().isBefore(LocalDateTime.now())) {
            return null;
        }

        // 5. Check token type
        if (!"REFRESH".equals(record.getTokenType())) {
            return null;
        }

        // 6. Return userId
        return record.getUserId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TokenPairVO refreshAccessToken(String rawRefreshToken) {
        // 1. Hash the refresh token
        String hash = tokenGenerator.hash(rawRefreshToken);

        // 2. Find by hash (use FOR UPDATE row lock for concurrent safety)
        TokenRecord record = tokenRepository.findByHashForUpdate(hash).orElse(null);
        if (record == null) {
            return null;
        }

        // 3. Check if the token is valid (not revoked, not expired, type=REFRESH)
        boolean isValid = !record.isRevoked()
                && (record.getExpiresAt() == null || !record.getExpiresAt().isBefore(LocalDateTime.now()))
                && "REFRESH".equals(record.getTokenType());

        if (isValid) {
            // a. Generate new token pair first (so we have the new hash for replaced_by)
            TokenGenerator.TokenPairRaw pair = tokenGenerator.generate();

            // b. Set replaced_by on old token, then revoke it
            tokenRepository.updateReplacedBy(hash, pair.getRefreshHash());
            tokenRepository.revoke(hash);

            // c. Save new access token
            TokenRecord newAccessRecord = new TokenRecord();
            newAccessRecord.setUserId(record.getUserId());
            newAccessRecord.setTokenHash(pair.getAccessHash());
            newAccessRecord.setTokenType("ACCESS");
            newAccessRecord.setExpiresAt(pair.getAccessExpiresAt());
            newAccessRecord.setCreatedAt(LocalDateTime.now());
            newAccessRecord.setRevoked(false);
            tokenRepository.save(newAccessRecord);

            // Save new refresh token (link old record to new refresh hash)
            TokenRecord newRefreshRecord = new TokenRecord();
            newRefreshRecord.setUserId(record.getUserId());
            newRefreshRecord.setTokenHash(pair.getRefreshHash());
            newRefreshRecord.setTokenType("REFRESH");
            newRefreshRecord.setExpiresAt(pair.getRefreshExpiresAt());
            newRefreshRecord.setCreatedAt(LocalDateTime.now());
            newRefreshRecord.setRevoked(false);
            tokenRepository.save(newRefreshRecord);

            // d. Return new TokenPairVO
            Users user = usersMapper.selectBatchIds(List.of(record.getUserId()))
                    .stream().findFirst().orElse(null);
            if (user == null) {
                return null;
            }

            long expiresIn = Duration.between(LocalDateTime.now(), pair.getAccessExpiresAt()).getSeconds();

            UserInfoVO userInfo = new UserInfoVO();
            userInfo.setId(user.getId());
            userInfo.setUsername(user.getUsername());
            userInfo.setNickname(user.getNickname());
            userInfo.setAvatar(user.getAvatar());
            userInfo.setEmail(user.getEmail());
            userInfo.setRole(user.getRole());

            return new TokenPairVO(pair.getRawAccessToken(), pair.getRawRefreshToken(), expiresIn, userInfo);
        }

        // 4. If found BUT revoked AND has replaced_by (multi-tab concurrency)
        if (record.isRevoked() && record.getReplacedBy() != null) {
            // a. Look up the replaced_by hash
            TokenRecord replacement = tokenRepository.findByHash(record.getReplacedBy()).orElse(null);
            if (replacement != null) {
                // b. If that token is still valid → generate a new pair from it
                boolean replacementValid = !replacement.isRevoked()
                        && (replacement.getExpiresAt() == null || !replacement.getExpiresAt().isBefore(LocalDateTime.now()))
                        && "REFRESH".equals(replacement.getTokenType());

                if (replacementValid) {
                    // Generate new token pair first (so we have the new hash for replaced_by)
                    TokenGenerator.TokenPairRaw pair = tokenGenerator.generate();

                    // Set replaced_by on replacement token, then revoke it (maintain the chain)
                    tokenRepository.updateReplacedBy(replacement.getTokenHash(), pair.getRefreshHash());
                    tokenRepository.revoke(replacement.getTokenHash());

                    // Save new tokens
                    TokenRecord newAccessRecord = new TokenRecord();
                    newAccessRecord.setUserId(replacement.getUserId());
                    newAccessRecord.setTokenHash(pair.getAccessHash());
                    newAccessRecord.setTokenType("ACCESS");
                    newAccessRecord.setExpiresAt(pair.getAccessExpiresAt());
                    newAccessRecord.setCreatedAt(LocalDateTime.now());
                    newAccessRecord.setRevoked(false);
                    tokenRepository.save(newAccessRecord);

                    TokenRecord newRefreshRecord = new TokenRecord();
                    newRefreshRecord.setUserId(replacement.getUserId());
                    newRefreshRecord.setTokenHash(pair.getRefreshHash());
                    newRefreshRecord.setTokenType("REFRESH");
                    newRefreshRecord.setExpiresAt(pair.getRefreshExpiresAt());
                    newRefreshRecord.setCreatedAt(LocalDateTime.now());
                    newRefreshRecord.setRevoked(false);
                    tokenRepository.save(newRefreshRecord);

                    Users user = usersMapper.selectBatchIds(List.of(replacement.getUserId()))
                            .stream().findFirst().orElse(null);
                    if (user == null) {
                        return null;
                    }

                    long expiresIn = Duration.between(LocalDateTime.now(), pair.getAccessExpiresAt()).getSeconds();

                    UserInfoVO userInfo = new UserInfoVO();
                    userInfo.setId(user.getId());
                    userInfo.setUsername(user.getUsername());
                    userInfo.setNickname(user.getNickname());
                    userInfo.setAvatar(user.getAvatar());
                    userInfo.setEmail(user.getEmail());
                    userInfo.setRole(user.getRole());

                    return new TokenPairVO(pair.getRawAccessToken(), pair.getRawRefreshToken(), expiresIn, userInfo);
                }
            }
        }

        return null;
    }

    @Override
    public void revokeToken(String rawToken) {
        String hash = tokenGenerator.hash(rawToken);
        tokenRepository.revoke(hash);
    }

    @Override
    public void revokeAllUserTokens(Long userId) {
        tokenRepository.revokeAllByUserId(userId);
    }
}
