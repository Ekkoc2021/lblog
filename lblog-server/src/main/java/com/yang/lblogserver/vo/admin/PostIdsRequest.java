package com.yang.lblogserver.vo.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Schema(description = "文章ID列表请求")
public class PostIdsRequest {
    @NotEmpty(message = "文章ID列表不能为空")
    @Schema(description = "文章ID数组")
    private List<Long> postIds;

    public List<Long> getPostIds() { return postIds; }
    public void setPostIds(List<Long> postIds) { this.postIds = postIds; }
}
