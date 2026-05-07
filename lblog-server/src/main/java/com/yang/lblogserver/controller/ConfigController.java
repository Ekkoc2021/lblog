package com.yang.lblogserver.controller;

import com.yang.lblogserver.common.ApiResponse;
import com.yang.lblogserver.storage.FileStorage;
import com.yang.lblogserver.vo.SiteConfigVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "站点配置", description = "公开的站点配置信息")
@RestController
@RequestMapping("/api/v1/config")
public class ConfigController {

    private static final long IMAGE_MAX_SIZE = 10 * 1024 * 1024; // 10MB

    private final FileStorage fileStorage;

    public ConfigController(FileStorage fileStorage) {
        this.fileStorage = fileStorage;
    }

    @Operation(summary = "获取站点配置", description = "返回图片基础 URL、大小限制等配置信息（无需登录）")
    @GetMapping
    public ApiResponse<SiteConfigVO> getConfig() {
        return ApiResponse.success(new SiteConfigVO(fileStorage.getBaseUrl(), IMAGE_MAX_SIZE));
    }
}
