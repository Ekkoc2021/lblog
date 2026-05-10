package com.yang.lblogserver.vo.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "上下篇文章信息")
public class PrevNextPostVO {
    @Schema(description = "文章ID")
    private Long id;
    @Schema(description = "文章标题")
    private String title;
    @Schema(description = "文章URL标识")
    private String slug;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
}
