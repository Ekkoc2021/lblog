package com.yang.lblogserver.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "作者申请请求")
public class ApplicationRequest {

    @NotBlank(message = "申请理由不能为空")
    @Schema(description = "申请理由/自我介绍")
    private String reason;

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
