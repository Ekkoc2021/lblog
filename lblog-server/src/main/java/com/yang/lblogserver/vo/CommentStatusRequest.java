package com.yang.lblogserver.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "评论审核请求")
public class CommentStatusRequest {

    @NotNull(message = "审核状态不能为空")
    @Schema(description = "1=通过 2=驳回")
    private Integer status;

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
