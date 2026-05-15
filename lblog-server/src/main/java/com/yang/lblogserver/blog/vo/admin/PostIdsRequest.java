package com.yang.lblogserver.blog.vo.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "文章ID列表请求")
public class PostIdsRequest {
    @NotNull(message = "文章ID列表不能为null")
    @Schema(description = "文章ID数组（传空列表可清空关联）")
    private List<Long> postIds;

    public List<Long> getPostIds() { return postIds; }
    public void setPostIds(List<Long> postIds) { this.postIds = postIds; }
}
