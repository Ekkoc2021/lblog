package com.yang.lblogserver.auth.security.repository;

import com.yang.lblogserver.auth.security.model.TokenRecord;

import java.util.Optional;

public interface TokenRepository {

    /** 存储一个 token 记录 */
    void save(TokenRecord record);

    /** 根据 tokenHash 查找 token 记录 */
    Optional<TokenRecord> findByHash(String tokenHash);

    /** 根据 tokenHash 查找并用 FOR UPDATE 锁行（用于 refresh rotation 并发控制） */
    Optional<TokenRecord> findByHashForUpdate(String tokenHash);

    /** 吊销单个 token */
    void revoke(String tokenHash);

    /** 吊销用户所有 token */
    void revokeAllByUserId(Long userId);

    /** 吊销用户所有指定类型的 token */
    void revokeAllByUserId(Long userId, String tokenType);

    /** 清理过期且已吊销的 token */
    int deleteExpired();

    /** 更新 token 的 replaced_by 字段（用于 refresh rotation 链式追踪） */
    void updateReplacedBy(String tokenHash, String replacedBy);

    /** 统计用户有效 token 数 */
    int countValidByUserId(Long userId);
}
