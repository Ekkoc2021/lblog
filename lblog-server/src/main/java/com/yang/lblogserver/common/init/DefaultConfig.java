package com.yang.lblogserver.common.init;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DefaultConfig {
    private DefaultConfig() {}

    private static final Map<String, String> ALL = new LinkedHashMap<>();
    static {
        // 基础配置
        ALL.put("registration_enabled", "true");
        ALL.put("site_title", "My Blog");
        ALL.put("ai_draw_chat_enabled", "true");
        ALL.put("image_cleanup_days", "0");
        ALL.put("reasoning_inject", "true");
        // Token 过期时间
        ALL.put("token_access_ttl", "7200");   // ACCESS 2h
        ALL.put("token_refresh_ttl", "604800"); // REFRESH 7d
        // 推荐排序
        ALL.put("rank.recommend.weight.like", "2.0");
        ALL.put("rank.recommend.weight.comment", "3.0");
        ALL.put("rank.recommend.weight.view", "0.05");
        ALL.put("rank.recommend.decay.base", "2");
        ALL.put("rank.recommend.decay.exponent", "1.2");
        // 最热排序
        ALL.put("rank.hot.weight.view", "0.1");
        ALL.put("rank.hot.weight.like", "1.0");
        ALL.put("rank.hot.weight.comment", "2.0");
        ALL.put("rank.hot.decay.base", "1");
        ALL.put("rank.hot.decay.exponent", "1.5");
    }

    public static String getDefault(String key) {
        return ALL.get(key);
    }
}
