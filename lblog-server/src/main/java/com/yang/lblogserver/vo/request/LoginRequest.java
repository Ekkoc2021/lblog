package com.yang.lblogserver.vo.request;

import io.swagger.v3.oas.annotations.media.Schema;

// TODO: 后续增加字段校验注解（@NotBlank 等）
@Schema(description = "登录请求")
public class LoginRequest {
    @Schema(description = "用户名")
    private String username;
    @Schema(description = "密码")
    private String password;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
