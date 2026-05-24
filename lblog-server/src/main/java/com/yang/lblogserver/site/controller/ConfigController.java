package com.yang.lblogserver.site.controller;

import com.yang.lblogserver.common.ApiResponse;
import com.yang.lblogserver.site.service.SiteConfigCacheService;
import com.yang.lblogserver.storage.FileStorage;
import com.yang.lblogserver.site.vo.SiteConfigVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@Tag(name = "站点配置", description = "公开的站点配置信息")
@RestController
@RequestMapping("/api/v1")
public class ConfigController {

    private static final long IMAGE_MAX_SIZE = 10 * 1024 * 1024;

    private final FileStorage fileStorage;
    private final SiteConfigCacheService siteConfigCacheService;

    public ConfigController(FileStorage fileStorage, SiteConfigCacheService siteConfigCacheService) {
        this.fileStorage = fileStorage;
        this.siteConfigCacheService = siteConfigCacheService;
    }

    @Operation(summary = "获取站点配置", description = "公开配置信息（无需登录）")
    @GetMapping("/config")
    public ApiResponse<SiteConfigVO> getConfig() {
        String regEnabled = siteConfigCacheService.getConfigValue("registration_enabled");
        String aiDrawChat = siteConfigCacheService.getConfigValue("ai_draw_chat_enabled");
        return ApiResponse.success(new SiteConfigVO(
                fileStorage.getBaseUrl(), IMAGE_MAX_SIZE,
                "true".equals(regEnabled), "true".equals(aiDrawChat)));
    }

    @Operation(summary = "设置注册开关", description = "管理员控制是否允许新用户注册")
    @PutMapping("/author/site-config/registration")
    public ApiResponse<?> setRegistrationEnabled(@RequestBody RegistrationToggleRequest request) {
        siteConfigCacheService.updateConfigValue("registration_enabled",
                String.valueOf(request.isEnabled()));
        return ApiResponse.success(null);
    }

    public static class RegistrationToggleRequest {
        private boolean enabled;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
