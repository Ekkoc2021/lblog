package com.yang.lblogserver.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "修改密码请求")
public class ChangePasswordRequest {
    @Schema(description = "旧密码")
    private String oldPassword;
    @Schema(description = "新密码")
    private String newPassword;

    public String getOldPassword() { return oldPassword; }
    public void setOldPassword(String oldPassword) { this.oldPassword = oldPassword; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}
