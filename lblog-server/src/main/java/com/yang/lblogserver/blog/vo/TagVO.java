package com.yang.lblogserver.blog.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "标签信息")
public class TagVO {
    @Schema(description = "标签ID")
    private Long id;
    @Schema(description = "标签名")
    private String name;
    @Schema(description = "URL标识")
    private String slug;
    @Schema(description = "关联文章数")
    private Integer postCount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public Integer getPostCount() { return postCount; }
    public void setPostCount(Integer postCount) { this.postCount = postCount; }
}
