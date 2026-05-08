package com.yang.lblogserver.vo.admin;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "更新用户请求")
public class UpdateUserRequest {

    @Schema(description = "显示名称")
    private String nickname;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "角色ID列表")
    private List<Long> roleIds;

    @Schema(description = "状态：1-正常 0-禁用")
    private Integer status;

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public List<Long> getRoleIds() { return roleIds; }
    public void setRoleIds(List<Long> roleIds) { this.roleIds = roleIds; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
