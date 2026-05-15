package com.yang.lblogserver.auth.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.NavigableSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 注册保护 — 两层防御：
 * <ul>
 *   <li>每 IP 30 分钟仅允许注册 1 次</li>
 *   <li>全局 5 秒滑动窗口超过 10 次 → 今日关闭注册</li>
 * </ul>
 */
@Service
public class RegisterProtectionService {

    private static final int IP_WINDOW_MINUTES = 30;
    private static final int GLOBAL_WINDOW_SECONDS = 5;
    private static final int GLOBAL_MAX_COUNT = 10;

    // 1) IP 级缓存：key = "register:ip:{ip}"，30 分钟过期
    private final Cache<String, Boolean> ipCache;

    // 2) 全局滑动窗口：存储最近 5s 内的时间戳
    private final Cache<String, NavigableSet<Long>> slidingWindowCache;

    // 3) 全局熔断标记
    private final AtomicBoolean globalBlocked = new AtomicBoolean(false);
    private final AtomicLong blockedUntilEpochMs = new AtomicLong(0);

    public RegisterProtectionService() {
        ipCache = Caffeine.newBuilder()
                .expireAfterWrite(IP_WINDOW_MINUTES, TimeUnit.MINUTES)
                .maximumSize(100_000)
                .build();

        slidingWindowCache = Caffeine.newBuilder()
                .expireAfterWrite(GLOBAL_WINDOW_SECONDS + 5, TimeUnit.SECONDS)
                .maximumSize(100)
                .build();
    }

    // ========== IP 级 ==========

    /** 该 IP 是否在 30 分钟冷却期内 */
    public boolean isIpBlocked(String ip) {
        return ipCache.getIfPresent(ip) != null;
    }

    /** 记录该 IP 注册了一次 */
    public void markIpRegistered(String ip) {
        ipCache.put(ip, Boolean.TRUE);
    }

    // ========== 全局滑动窗口 ==========

    /**
     * 检查全局速率并记录本次注册。
     * @return true = 允许注册，false = 触发熔断
     */
    public boolean checkGlobalRate() {
        long now = System.currentTimeMillis();

        // 如果已熔断且未到午夜
        if (globalBlocked.get()) {
            if (now < blockedUntilEpochMs.get()) {
                return false;
            }
            // 过了午夜，自动重置
            globalBlocked.set(false);
        }

        // 滑动窗口：维护一个有序时间戳集合
        NavigableSet<Long> window = slidingWindowCache.get("global", k -> new ConcurrentSkipListSet<>());
        synchronized (window) {
            window.add(now);
            // 清除超过 5 秒的旧时间戳
            window.headSet(now - GLOBAL_WINDOW_SECONDS * 1000L).clear();

            if (window.size() > GLOBAL_MAX_COUNT) {
                // 触发熔断到今晚午夜
                LocalDateTime midnight = LocalDate.now().plusDays(1).atStartOfDay();
                blockedUntilEpochMs.set(midnight.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
                globalBlocked.set(true);
                return false;
            }
        }
        return true;
    }

    // ========== 工具 ==========

    /** 从请求中提取客户端 IP */
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

    /** 熔断是否仍然有效 */
    public boolean isGloballyBlocked() {
        if (!globalBlocked.get()) return false;
        if (System.currentTimeMillis() >= blockedUntilEpochMs.get()) {
            globalBlocked.set(false);
            return false;
        }
        return true;
    }

    /** 熔断剩余秒数（用于返回给客户端） */
    public long globalBlockedRemainingSeconds() {
        long remaining = (blockedUntilEpochMs.get() - System.currentTimeMillis()) / 1000;
        return Math.max(0, remaining);
    }
}
