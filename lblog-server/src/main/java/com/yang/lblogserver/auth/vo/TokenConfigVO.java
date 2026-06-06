package com.yang.lblogserver.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "Token 过期时间配置")
public class TokenConfigVO {

    @Schema(description = "ACCESS 过期秒数，范围 [300, 86400]")
    @Min(value = 300, message = "ACCESS 过期时间不能小于 300 秒（5 分钟）")
    @Max(value = 86400, message = "ACCESS 过期时间不能大于 86400 秒（24 小时）")
    private Long accessTtl;

    @Schema(description = "REFRESH 过期秒数，范围 [1800, 2592000]")
    @Min(value = 1800, message = "REFRESH 过期时间不能小于 1800 秒（30 分钟）")
    @Max(value = 2592000, message = "REFRESH 过期时间不能大于 2592000 秒（30 天）")
    private Long refreshTtl;

    public Long getAccessTtl() { return accessTtl; }
    public void setAccessTtl(Long accessTtl) { this.accessTtl = accessTtl; }
    public Long getRefreshTtl() { return refreshTtl; }
    public void setRefreshTtl(Long refreshTtl) { this.refreshTtl = refreshTtl; }
}
