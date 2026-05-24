package com.yang.lblogserver.site.controller;

import com.yang.lblogserver.common.ApiResponse;
import com.yang.lblogserver.site.domain.SiteConfig;
import com.yang.lblogserver.site.mapper.SiteConfigMapper;
import com.yang.lblogserver.site.service.SiteConfigCacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "管理端", description = "站点配置管理")
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminConfigController {

    private final SiteConfigMapper siteConfigMapper;
    private final SiteConfigCacheService siteConfigCacheService;

    public AdminConfigController(SiteConfigMapper siteConfigMapper,
                                 SiteConfigCacheService siteConfigCacheService) {
        this.siteConfigMapper = siteConfigMapper;
        this.siteConfigCacheService = siteConfigCacheService;
    }

    @Operation(summary = "获取全部配置")
    @GetMapping("/configs")
    public ApiResponse<List<SiteConfig>> getConfigs() {
        List<SiteConfig> list = siteConfigMapper.selectAll();
        return ApiResponse.success(list);
    }

    @Operation(summary = "批量更新配置", description = "只传需要改的项，不传的保持不变")
    @PutMapping("/configs")
    public ApiResponse<?> updateConfigs(@RequestBody Map<String, String> configs) {
        for (Map.Entry<String, String> entry : configs.entrySet()) {
            siteConfigMapper.updateConfigValue(entry.getKey(), entry.getValue());
            siteConfigCacheService.refreshCache(entry.getKey());
        }
        return ApiResponse.success(null);
    }

    @Operation(summary = "添加配置")
    @PostMapping("/configs")
    public ApiResponse<?> addConfig(@RequestBody SiteConfig config) {
        siteConfigMapper.updateConfigValue(config.getConfigKey(), config.getConfigValue());
        siteConfigCacheService.refreshCache(config.getConfigKey());
        return ApiResponse.success(null);
    }

    @Operation(summary = "删除配置")
    @DeleteMapping("/configs")
    public ApiResponse<?> deleteConfig(@RequestParam("key") String key) {
        siteConfigMapper.deleteByKey(key);
        siteConfigCacheService.refreshCache(key);
        return ApiResponse.success(null);
    }

    /**
     * 配置管理接口全部已实现 (Bug 3 确认)：
     * - GET    /api/v1/admin/configs          ✅ 获取全部配置
     * - PUT    /api/v1/admin/configs          ✅ 批量更新配置
     * - POST   /api/v1/admin/configs          ✅ 添加配置
     * - DELETE /api/v1/admin/configs?key=xxx  ✅ 删除配置
     */
}
