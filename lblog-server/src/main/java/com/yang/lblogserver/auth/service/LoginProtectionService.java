package com.yang.lblogserver.auth.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.NavigableSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 登录保护 — 两层防御：
 * <ul>
 *   <li>IP 级别：2 分钟内同一 IP 失败超过 15 次则封锁该 IP 30 分钟</li>
 *   <li>全局熔断：5 秒内全站失败超过 50 次 → 关闭登录 1 小时</li>
 * </ul>
 */
@Service
public class LoginProtectionService {

    private static final int IP_BLOCK_THRESHOLD = 15;
    private static final int IP_WINDOW_MINUTES = 2;
    private static final int IP_BLOCK_DURATION_MINUTES = 30;
    private static final int GLOBAL_WINDOW_SECONDS = 5;
    private static final int GLOBAL_MAX_COUNT = 50;
    private static final long GLOBAL_BLOCK_DURATION_MS = 3600_000; // 1 小时

    private final Cache<String, AtomicInteger> ipFailures;
    private final Cache<String, Boolean> ipBlocked;
    private final Cache<String, NavigableSet<Long>> slidingWindow;

    private final AtomicBoolean globalBlocked = new AtomicBoolean(false);
    private final AtomicLong blockedUntilEpochMs = new AtomicLong(0);

    public LoginProtectionService() {
        ipFailures = Caffeine.newBuilder()
                .expireAfterWrite(IP_WINDOW_MINUTES, TimeUnit.MINUTES)
                .maximumSize(100_000)
                .build();
        ipBlocked = Caffeine.newBuilder()
                .expireAfterWrite(IP_BLOCK_DURATION_MINUTES, TimeUnit.MINUTES)
                .maximumSize(100_000)
                .build();
        slidingWindow = Caffeine.newBuilder()
                .expireAfterWrite(GLOBAL_WINDOW_SECONDS + 5, TimeUnit.SECONDS)
                .maximumSize(100)
                .build();
    }

    // ─── IP 级 ───

    public boolean isIpBlocked(String ip) {
        return ipBlocked.getIfPresent(ip) != null;
    }

    public void recordIpFailure(String ip) {
        ipFailures.asMap().compute(ip, (k, v) -> {
            if (v == null) return new AtomicInteger(1);
            int count = v.incrementAndGet();
            if (count >= IP_BLOCK_THRESHOLD) {
                ipBlocked.put(ip, Boolean.TRUE);
            }
            return v;
        });
    }

    public void clearIp(String ip) {
        ipFailures.invalidate(ip);
        ipBlocked.invalidate(ip);
    }

    // ─── 全局熔断 ───

    public boolean checkGlobalRate() {
        long now = System.currentTimeMillis();

        if (globalBlocked.get()) {
            if (now < blockedUntilEpochMs.get()) {
                return false;
            }
            globalBlocked.set(false);
        }

        NavigableSet<Long> window = slidingWindow.get("global", k -> new ConcurrentSkipListSet<>());
        synchronized (window) {
            window.add(now);
            window.headSet(now - GLOBAL_WINDOW_SECONDS * 1000L).clear();

            if (window.size() > GLOBAL_MAX_COUNT) {
                blockedUntilEpochMs.set(now + GLOBAL_BLOCK_DURATION_MS);
                globalBlocked.set(true);
                return false;
            }
        }
        return true;
    }

    public boolean isGloballyBlocked() {
        if (!globalBlocked.get()) return false;
        if (System.currentTimeMillis() >= blockedUntilEpochMs.get()) {
            globalBlocked.set(false);
            return false;
        }
        return true;
    }

    public long globalBlockedRemainingSeconds() {
        long remaining = (blockedUntilEpochMs.get() - System.currentTimeMillis()) / 1000;
        return Math.max(0, remaining);
    }

    // ─── 工具 ───

    public String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty() && !"unknown".equalsIgnoreCase(xff)) {
            return xff.split(",")[0].trim();
        }
        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isEmpty() && !"unknown".equalsIgnoreCase(xri)) {
            return xri;
        }
        return request.getRemoteAddr();
    }
}
