package com.yang.lblogserver.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "创建成功响应（返回ID）")
public class IdResponse {
    @Schema(description = "新创建记录的ID")
    private Long id;

    public IdResponse() {}

    public IdResponse(Long id) {
        this.id = id;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
}
