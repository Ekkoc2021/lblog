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
 * 登录保护 — 两层防御（内存级，配合 {@link LoginAttemptService} 用户名级限流构成三层体系）。
 *
 * <h3>防御层次</h3>
 * <ol>
 *   <li><b>全局熔断</b>：5 秒滑动窗口内全站登录失败超过 50 次 → 封锁整个登录接口 1 小时。
 *       用于识别和阻断大规模分布式暴力破解。封锁期间所有 IP 均被拒绝。</li>
 *   <li><b>IP 级限流</b>：同一 IP 在 2 分钟窗口内失败超过 15 次 → 将该 IP 加入黑名单 30 分钟。
 *       两个缓存独立工作：ipFailures（2 分钟计数窗口）+ ipBlocked（30 分钟封锁）。
 *       正常人在 2 分钟内不会输错 15 次密码，脚本一击即中。</li>
 * </ol>
 *
 * <h3>设计考虑</h3>
 * <ul>
 *   <li>所有数据存储在 Caffeine 本地缓存中，单机内存占用约 12 MB（100K IP 上限）。</li>
 *   <li>登录成功后 IP 封锁会被清除（{@link #clearIp}），正常用户不受影响。</li>
 *   <li>{@code X-Forwarded-For} 头可被伪造 —— 当前为单机反向代理部署，由 Nginx 覆盖该头，
 *       不直接暴露端口到公网。若改为直连部署需要切换为 {@code request.getRemoteAddr()}。</li>
 * </ul>
 */
@Service
public class LoginProtectionService {

    // IP 级：2 分钟内 15 次失败 → 加入 ipBlocked 黑名单
    private static final int IP_BLOCK_THRESHOLD = 15;
    private static final int IP_WINDOW_MINUTES = 2;
    // IP 封锁持续时间
    private static final int IP_BLOCK_DURATION_MINUTES = 30;
    // 全局熔断：5 秒滑动窗口
    private static final int GLOBAL_WINDOW_SECONDS = 5;
    private static final int GLOBAL_MAX_COUNT = 50;
    // 全局封锁 1 小时（毫秒）
    private static final long GLOBAL_BLOCK_DURATION_MS = 3600_000;

    /** IP 失败计数器缓存：2 分钟过期，记录每个 IP 的最近失败次数 */
    private final Cache<String, AtomicInteger> ipFailures;
    /** IP 黑名单缓存：30 分钟过期，命中即被封锁 */
    private final Cache<String, Boolean> ipBlocked;
    /** 全局滑动窗口：以时间戳集合统计最近 5 秒的失败请求数 */
    private final Cache<String, NavigableSet<Long>> slidingWindow;

    /** 全局熔断标记 */
    private final AtomicBoolean globalBlocked = new AtomicBoolean(false);
    /** 全局熔断解除的时间戳（毫秒） */
    private final AtomicLong blockedUntilEpochMs = new AtomicLong(0);

    public LoginProtectionService() {
        // IP 失败计数：2 分钟滑动窗口，最多 10 万个 IP
        ipFailures = Caffeine.newBuilder()
                .expireAfterWrite(IP_WINDOW_MINUTES, TimeUnit.MINUTES)
                .maximumSize(100_000)
                .build();
        // IP 黑名单：30 分钟自动解除，最多 10 万个 IP
        ipBlocked = Caffeine.newBuilder()
                .expireAfterWrite(IP_BLOCK_DURATION_MINUTES, TimeUnit.MINUTES)
                .maximumSize(100_000)
                .build();
        // 全局滑动窗口：10 秒自动清除（窗口 5s + 缓冲 5s），最多 100 个 key
        slidingWindow = Caffeine.newBuilder()
                .expireAfterWrite(GLOBAL_WINDOW_SECONDS + 5, TimeUnit.SECONDS)
                .maximumSize(100)
                .build();
    }

    // ─── IP 级防御 ────────────────────────────────────────────────

    /** 该 IP 是否已被封锁 */
    public boolean isIpBlocked(String ip) {
        return ipBlocked.getIfPresent(ip) != null;
    }

    /**
     * 记录一次 IP 级别的登录失败。
     * 当计数达到阈值（15 次/2 分钟）时，自动将该 IP 加入 30 分钟黑名单。
     */
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

    /** 登录成功后清除该 IP 的所有记录（失败计数 + 黑名单） */
    public void clearIp(String ip) {
        ipFailures.invalidate(ip);
        ipBlocked.invalidate(ip);
    }

    // ─── 全局熔断 ────────────────────────────────────────────────

    /**
     * 检查全局速率并记录本次失败。
     * 使用 5 秒滑动窗口（ConcurrentSkipListSet）统计全站失败次数。
     * 超过阈值 50 次时触发全局熔断，封锁登录 1 小时。
     *
     * @return true 允许继续，false 触发熔断
     */
    public boolean checkGlobalRate() {
        long now = System.currentTimeMillis();

        // 检查是否已熔断中
        if (globalBlocked.get()) {
            // 封锁时间未到 → 仍拒绝
            if (now < blockedUntilEpochMs.get()) {
                return false;
            }
            // 封锁时间已过 → 自动解除
            globalBlocked.set(false);
        }

        // 5 秒滑动窗口：维护一个有序时间戳集合
        NavigableSet<Long> window = slidingWindow.get("global", k -> new ConcurrentSkipListSet<>());
        synchronized (window) {
            window.add(now);
            // 清除超过 5 秒的旧时间戳
            window.headSet(now - GLOBAL_WINDOW_SECONDS * 1000L).clear();

            if (window.size() > GLOBAL_MAX_COUNT) {
                // 触发熔断：封锁登录 1 小时
                blockedUntilEpochMs.set(now + GLOBAL_BLOCK_DURATION_MS);
                globalBlocked.set(true);
                return false;
            }
        }
        return true;
    }

    /** 全局熔断是否当前有效（惰性检查：过了封锁时间自动解除） */
    public boolean isGloballyBlocked() {
        if (!globalBlocked.get()) return false;
        // 惰性解除：每次调用时检查是否已过封锁时间
        if (System.currentTimeMillis() >= blockedUntilEpochMs.get()) {
            globalBlocked.set(false);
            return false;
        }
        return true;
    }

    /** 全局熔断剩余秒数（用于客户端提示） */
    public long globalBlockedRemainingSeconds() {
        long remaining = (blockedUntilEpochMs.get() - System.currentTimeMillis()) / 1000;
        return Math.max(0, remaining);
    }

    // ─── 工具方法 ────────────────────────────────────────────────

    /**
     * 从请求中提取客户端真实 IP。
     * 优先级：X-Forwarded-For → X-Real-IP → RemoteAddr。
     * 注意：X-Forwarded-For 依赖反向代理（Nginx）覆盖，直接暴露公网时不可信。
     */
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
