package com.yang.lblogserver.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "登录响应")
public class LoginResponse {
    @Schema(description = "Token")
    private String token;
    @Schema(description = "用户信息")
    private UserInfoVO user;

    public LoginResponse() {}

    public LoginResponse(String token, UserInfoVO user) {
        this.token = token;
        this.user = user;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public UserInfoVO getUser() { return user; }
    public void setUser(UserInfoVO user) { this.user = user; }
}
