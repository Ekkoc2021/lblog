package com.yang.lblogserver.security.repository;

import com.yang.lblogserver.domain.UserToken;
import com.yang.lblogserver.mapper.UserTokenMapper;
import com.yang.lblogserver.security.model.TokenRecord;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public class DbTokenRepository implements TokenRepository {

    private final UserTokenMapper userTokenMapper;

    public DbTokenRepository(UserTokenMapper userTokenMapper) {
        this.userTokenMapper = userTokenMapper;
    }

    @Override
    public void save(TokenRecord record) {
        UserToken entity = toEntity(record);
        userTokenMapper.insert(entity);
        record.setId(entity.getId());
    }

    @Override
    public Optional<TokenRecord> findByHash(String tokenHash) {
        UserToken entity = userTokenMapper.findByTokenHash(tokenHash);
        return Optional.ofNullable(entity).map(this::toRecord);
    }

    @Override
    public Optional<TokenRecord> findByHashForUpdate(String tokenHash) {
        UserToken entity = userTokenMapper.findByTokenHashForUpdate(tokenHash);
        return Optional.ofNullable(entity).map(this::toRecord);
    }

    @Override
    public void revoke(String tokenHash) {
        userTokenMapper.revoke(tokenHash);
    }

    @Override
    public void revokeAllByUserId(Long userId) {
        userTokenMapper.revokeAllByUserId(userId);
    }

    @Override
    public void revokeAllByUserId(Long userId, String tokenType) {
        userTokenMapper.revokeAllByUserIdAndType(userId, tokenType);
    }

    @Override
    public int deleteExpired() {
        return userTokenMapper.deleteExpired();
    }

    @Override
    public int countValidByUserId(Long userId) {
        return userTokenMapper.countValidByUserId(userId);
    }

    private UserToken toEntity(TokenRecord record) {
        UserToken entity = new UserToken();
        entity.setId(record.getId());
        entity.setUserId(record.getUserId());
        entity.setTokenHash(record.getTokenHash());
        entity.setTokenType(record.getTokenType());
        entity.setExpiresAt(record.getExpiresAt());
        entity.setCreatedAt(record.getCreatedAt());
        entity.setRevoked(record.isRevoked());
        entity.setReplacedBy(record.getReplacedBy());
        return entity;
    }

    private TokenRecord toRecord(UserToken entity) {
        TokenRecord record = new TokenRecord();
        record.setId(entity.getId());
        record.setUserId(entity.getUserId());
        record.setTokenHash(entity.getTokenHash());
        record.setTokenType(entity.getTokenType());
        record.setExpiresAt(entity.getExpiresAt());
        record.setCreatedAt(entity.getCreatedAt());
        record.setRevoked(entity.getRevoked() != null && entity.getRevoked());
        record.setReplacedBy(entity.getReplacedBy());
        return record;
    }
}
