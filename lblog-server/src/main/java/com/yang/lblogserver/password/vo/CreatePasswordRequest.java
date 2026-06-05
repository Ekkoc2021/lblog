package com.yang.lblogserver.password.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "创建密码记录请求")
public class CreatePasswordRequest {

    @NotBlank @Size(max = 100)
    @Schema(description = "网站名称", required = true)
    private String siteName;

    @Size(max = 500)
    @Schema(description = "网址")
    private String siteUrl;

    @NotBlank @Size(max = 200)
    @Schema(description = "账号", required = true)
    private String username;

    @NotBlank @Size(max = 5000)
    @Schema(description = "加密后的密码密文", required = true)
    private String encryptedPassword;

    @Size(max = 500)
    @Schema(description = "备注")
    private String note;

    public String getSiteName() { return siteName; }
    public void setSiteName(String siteName) { this.siteName = siteName; }
    public String getSiteUrl() { return siteUrl; }
    public void setSiteUrl(String siteUrl) { this.siteUrl = siteUrl; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEncryptedPassword() { return encryptedPassword; }
    public void setEncryptedPassword(String encryptedPassword) { this.encryptedPassword = encryptedPassword; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
