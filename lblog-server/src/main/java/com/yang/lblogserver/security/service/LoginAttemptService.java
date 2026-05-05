package com.yang.lblogserver.security.service;

/**
 * 登录防暴力破解 — 记录失败次数、判定拦截。
 * <p>
 * 当前实现基于 Caffeine 本地缓存，后续可提供 Redis 实现，
 * 只需实现此接口并通过 {@code @Primary} 切换即可。
 * </p>
 */
public interface LoginAttemptService {

    /** 记录一次失败尝试 */
    void recordFailedAttempt(String username);

    /** 记录成功登录（清空失败计数） */
    void resetAttempts(String username);

    /** 当前用户是否被拦截（5 分钟内失败 ≥ 5 次） */
    boolean isBlocked(String username);

    /** 当前失败次数 */
    int getAttemptCount(String username);
}
