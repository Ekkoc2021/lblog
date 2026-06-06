package com.yang.lblogserver.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "审核请求")
public class ApplicationReviewRequest {

    @NotNull(message = "审核状态不能为空")
    @Min(1) @Max(3)
    @Schema(description = "审核结果：1=通过 2=拒绝 3=需补充")
    private Integer status;

    @Schema(description = "审核反馈（拒绝或需补充时必填）")
    private String feedback;

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }
}
