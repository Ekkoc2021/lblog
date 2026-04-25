package com.yang.lblogserver.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "分类信息")
public class CategoryVO {
    @Schema(description = "分类ID")
    private Long id;
    @Schema(description = "分类名")
    private String name;
    @Schema(description = "URL标识")
    private String slug;
    @Schema(description = "父分类ID")
    private Long parentId;
    @Schema(description = "分类描述")
    private String description;
    @Schema(description = "排序")
    private Integer sortOrder;
    @Schema(description = "文章数量")
    private Integer postCount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Integer getPostCount() { return postCount; }
    public void setPostCount(Integer postCount) { this.postCount = postCount; }
}
