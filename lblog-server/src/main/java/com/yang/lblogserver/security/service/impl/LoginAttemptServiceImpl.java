package com.yang.lblogserver.security.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yang.lblogserver.security.service.LoginAttemptService;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Caffeine 本地缓存实现，5 分钟内连续失败 5 次则拦截。
 * <p>
 * 如需迁移到 Redis，仅需另实现 {@link LoginAttemptService} 并标注 {@code @Primary}，
 * 此类的 {@code @Service} 改为 {@code @Service @ConditionalOnMissingBean(LoginAttemptService.class)} 即可。
 * </p>
 */
@Service
public class LoginAttemptServiceImpl implements LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final int BLOCK_DURATION_MINUTES = 5;

    private final Cache<String, AtomicInteger> attemptsCache;

    public LoginAttemptServiceImpl() {
        this.attemptsCache = Caffeine.newBuilder()
                .expireAfterWrite(BLOCK_DURATION_MINUTES, TimeUnit.MINUTES)
                .maximumSize(10_000)
                .build();
    }

    @Override
    public void recordFailedAttempt(String username) {
        attemptsCache.asMap()
                .compute(username, (key, val) -> {
                    if (val == null) {
                        return new AtomicInteger(1);
                    }
                    val.incrementAndGet();
                    return val;
                });
    }

    @Override
    public void resetAttempts(String username) {
        attemptsCache.invalidate(username);
    }

    @Override
    public boolean isBlocked(String username) {
        AtomicInteger count = attemptsCache.getIfPresent(username);
        return count != null && count.get() >= MAX_ATTEMPTS;
    }

    @Override
    public int getAttemptCount(String username) {
        AtomicInteger count = attemptsCache.getIfPresent(username);
        return count != null ? count.get() : 0;
    }
}
