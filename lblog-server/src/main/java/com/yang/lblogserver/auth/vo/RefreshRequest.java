package com.yang.lblogserver.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "刷新令牌请求")
public class RefreshRequest {
    @Schema(description = "刷新令牌")
    private String refreshToken;

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
}
