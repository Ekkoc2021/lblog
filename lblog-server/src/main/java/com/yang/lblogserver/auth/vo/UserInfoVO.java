package com.yang.lblogserver.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "用户信息")
public class UserInfoVO {
    @Schema(description = "用户ID")
    private Long id;
    @Schema(description = "登录名")
    private String username;
    @Schema(description = "显示名称")
    private String nickname;
    @Schema(description = "头像URL")
    private String avatar;
    @Schema(description = "邮箱")
    private String email;
    @Schema(description = "角色")
    private String role;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
