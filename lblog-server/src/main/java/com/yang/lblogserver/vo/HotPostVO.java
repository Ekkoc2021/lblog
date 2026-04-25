package com.yang.lblogserver.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "热门文章")
public class HotPostVO {
    @Schema(description = "文章ID")
    private Long id;
    @Schema(description = "文章标题")
    private String title;
    @Schema(description = "URL标识")
    private String slug;
    @Schema(description = "浏览量")
    private Integer viewCount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public Integer getViewCount() { return viewCount; }
    public void setViewCount(Integer viewCount) { this.viewCount = viewCount; }
}
