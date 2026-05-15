package com.yang.lblogserver.blog.vo.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Schema(description = "批量操作文章请求")
public class BatchPostsRequest {

    @NotEmpty(message = "文章ID列表不能为空")
    @Schema(description = "文章ID数组")
    private List<Long> ids;

    @NotBlank(message = "操作类型不能为空")
    @Schema(description = "操作类型: PUBLISH/DRAFT/DELETE")
    private String action;

    public List<Long> getIds() { return ids; }

    public void setIds(List<Long> ids) { this.ids = ids; }

    public String getAction() { return action; }

    public void setAction(String action) { this.action = action; }
}
