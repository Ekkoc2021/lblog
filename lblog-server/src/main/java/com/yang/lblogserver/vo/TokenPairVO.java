package com.yang.lblogserver.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "令牌响应")
public class TokenPairVO {
    @Schema(description = "访问令牌")
    private String accessToken;
    @Schema(description = "刷新令牌")
    private String refreshToken;
    @Schema(description = "过期时间（秒）")
    private Long expiresIn;
    @Schema(description = "用户信息")
    private UserInfoVO user;

    public TokenPairVO() {}

    public TokenPairVO(String accessToken, String refreshToken, Long expiresIn, UserInfoVO user) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
        this.user = user;
    }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    public Long getExpiresIn() { return expiresIn; }
    public void setExpiresIn(Long expiresIn) { this.expiresIn = expiresIn; }
    public UserInfoVO getUser() { return user; }
    public void setUser(UserInfoVO user) { this.user = user; }
}
