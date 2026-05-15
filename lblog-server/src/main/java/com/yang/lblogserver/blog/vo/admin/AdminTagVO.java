package com.yang.lblogserver.blog.vo.admin;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "管理端标签信息（含创建者）")
public class AdminTagVO {

    @Schema(description = "标签ID")
    private Long id;

    @Schema(description = "标签名")
    private String name;

    @Schema(description = "URL标识")
    private String slug;

    @Schema(description = "关联文章数")
    private Integer postCount;

    @Schema(description = "创建者用户ID")
    private Long createdBy;

    @Schema(description = "创建者昵称")
    private String creatorName;

    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }

    public void setName(String name) { this.name = name; }

    public String getSlug() { return slug; }

    public void setSlug(String slug) { this.slug = slug; }

    public Integer getPostCount() { return postCount; }

    public void setPostCount(Integer postCount) { this.postCount = postCount; }

    public Long getCreatedBy() { return createdBy; }

    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public String getCreatorName() { return creatorName; }

    public void setCreatorName(String creatorName) { this.creatorName = creatorName; }
}
