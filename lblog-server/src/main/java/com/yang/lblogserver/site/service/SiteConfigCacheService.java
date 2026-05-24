package com.yang.lblogserver.site.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yang.lblogserver.site.mapper.SiteConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class SiteConfigCacheService {

    private static final Logger log = LoggerFactory.getLogger(SiteConfigCacheService.class);

    private final SiteConfigMapper siteConfigMapper;

    private final Cache<String, String> cache = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .maximumSize(50)
            .build();

    public SiteConfigCacheService(SiteConfigMapper siteConfigMapper) {
        this.siteConfigMapper = siteConfigMapper;
    }

    public String getConfigValue(String key) {
        String cached = cache.getIfPresent(key);
        if (cached != null) {
            log.debug("cache hit: site_config.{}", key);
            return cached;
        }

        log.debug("cache miss: site_config.{}, loading from DB", key);
        String value = siteConfigMapper.selectConfigValue(key);
        if (value != null) {
            cache.put(key, value);
        }
        return value;
    }

    public void updateConfigValue(String key, String value) {
        siteConfigMapper.updateConfigValue(key, value);
        cache.invalidate(key);
        log.debug("site_config updated and cache invalidated: {}", key);
    }

    public void refreshCache(String key) {
        cache.invalidate(key);
        log.debug("cache invalidated: site_config.{}", key);
    }
}
