package com.yang.lblogserver.blog.service;

import com.yang.lblogserver.site.service.SiteConfigCacheService;
import org.springframework.stereotype.Service;

@Service
public class RankConfigService {

    private final SiteConfigCacheService configCache;

    public RankConfigService(SiteConfigCacheService configCache) {
        this.configCache = configCache;
    }

    public RankConfig getRecommendConfig() {
        return new RankConfig(
            getDouble("rank.recommend.weight.like", 2.0),
            getDouble("rank.recommend.weight.comment", 3.0),
            getDouble("rank.recommend.weight.view", 0.05),
            getInt("rank.recommend.decay.base", 2),
            getDouble("rank.recommend.decay.exponent", 1.2)
        );
    }

    public RankConfig getHotConfig() {
        return new RankConfig(
            getDouble("rank.hot.weight.like", 1.0),
            getDouble("rank.hot.weight.comment", 2.0),
            getDouble("rank.hot.weight.view", 0.1),
            getInt("rank.hot.decay.base", 1),
            getDouble("rank.hot.decay.exponent", 1.5)
        );
    }

    private double getDouble(String key, double defaultVal) {
        String val = configCache.getConfigValue(key);
        if (val == null || val.isBlank()) return defaultVal;
        try { return Double.parseDouble(val.trim()); }
        catch (NumberFormatException e) { return defaultVal; }
    }

    private int getInt(String key, int defaultVal) {
        String val = configCache.getConfigValue(key);
        if (val == null || val.isBlank()) return defaultVal;
        try { return Integer.parseInt(val.trim()); }
        catch (NumberFormatException e) { return defaultVal; }
    }
}
