package com.yang.lblogserver.blog.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "专栏文章信息")
public class SeriesPostVO {
    @Schema(description = "文章ID")
    private Long postId;
    @Schema(description = "文章标题")
    private String title;
    @Schema(description = "文章URL标识")
    private String slug;
    @Schema(description = "排序序号")
    private Integer sortOrder;

    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
