package com.yang.lblogserver.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "活跃会话")
public class SessionVO {

    @Schema(description = "Token ID")
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "Token 类型：ACCESS / REFRESH")
    private String tokenType;

    @Schema(description = "Token 脱敏预览（hash 前8位+****）")
    private String tokenPreview;

    @Schema(description = "创建时间")
    private String createdAt;

    @Schema(description = "过期时间")
    private String expiresAt;

    @Schema(description = "是否即将过期（<30min）")
    private boolean expiringSoon;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }
    public String getTokenPreview() { return tokenPreview; }
    public void setTokenPreview(String tokenPreview) { this.tokenPreview = tokenPreview; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getExpiresAt() { return expiresAt; }
    public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }
    public boolean isExpiringSoon() { return expiringSoon; }
    public void setExpiringSoon(boolean expiringSoon) { this.expiringSoon = expiringSoon; }
}
