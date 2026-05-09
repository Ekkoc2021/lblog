package com.yang.lblogserver.vo.admin;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "管理端专栏信息（含创建者）")
public class AdminSeriesVO {

    @Schema(description = "专栏ID")
    private Long id;

    @Schema(description = "专栏名称")
    private String title;

    @Schema(description = "URL标识")
    private String slug;

    @Schema(description = "专栏简介")
    private String description;

    @Schema(description = "封面图URL")
    private String coverImageUrl;

    @Schema(description = "所属分类ID")
    private Long categoryId;

    @Schema(description = "0-连载中, 1-已完结")
    private Integer isCompleted;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "文章数量")
    private Integer postCount;

    @Schema(description = "创建者用户ID")
    private Long createdBy;

    @Schema(description = "创建者昵称")
    private String creatorName;

    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }

    public void setTitle(String title) { this.title = title; }

    public String getSlug() { return slug; }

    public void setSlug(String slug) { this.slug = slug; }

    public String getDescription() { return description; }

    public void setDescription(String description) { this.description = description; }

    public String getCoverImageUrl() { return coverImageUrl; }

    public void setCoverImageUrl(String coverImageUrl) { this.coverImageUrl = coverImageUrl; }

    public Long getCategoryId() { return categoryId; }

    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public Integer getIsCompleted() { return isCompleted; }

    public void setIsCompleted(Integer isCompleted) { this.isCompleted = isCompleted; }

    public Integer getSortOrder() { return sortOrder; }

    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public Integer getPostCount() { return postCount; }

    public void setPostCount(Integer postCount) { this.postCount = postCount; }

    public Long getCreatedBy() { return createdBy; }

    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public String getCreatorName() { return creatorName; }

    public void setCreatorName(String creatorName) { this.creatorName = creatorName; }
}
