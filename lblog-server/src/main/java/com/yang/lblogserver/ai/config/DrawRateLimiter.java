package com.yang.lblogserver.ai.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class DrawRateLimiter {

    private static final int MAX_REQUESTS_PER_MINUTE = 10;

    private final Cache<String, Integer> requestCounts;

    public DrawRateLimiter() {
        this.requestCounts = Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .build();
    }

    public boolean tryAcquire(String ip) {
        Integer count = requestCounts.getIfPresent(ip);
        if (count == null) {
            requestCounts.put(ip, 1);
            return true;
        }
        if (count >= MAX_REQUESTS_PER_MINUTE) {
            return false;
        }
        requestCounts.put(ip, count + 1);
        return true;
    }
}
