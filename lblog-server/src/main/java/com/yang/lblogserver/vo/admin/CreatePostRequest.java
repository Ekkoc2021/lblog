package com.yang.lblogserver.vo.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "创建文章请求")
public class CreatePostRequest {
    @NotBlank(message = "标题不能为空")
    @Schema(description = "文章标题")
    private String title;

    @NotBlank(message = "URL别名不能为空")
    @Schema(description = "URL别名")
    private String slug;

    @Schema(description = "文章摘要")
    private String excerpt;

    @NotBlank(message = "正文不能为空")
    @Schema(description = "Markdown正文")
    private String body;

    @Schema(description = "特色图片URL")
    private String featuredImage;

    @NotNull(message = "状态不能为空")
    @Schema(description = "0-草稿, 1-已发布, 2-私密")
    private Integer status;

    @Schema(description = "分类ID")
    private Long categoryId;

    @Schema(description = "标签ID列表")
    private List<Long> tagIds;

    @Schema(description = "专栏ID")
    private Long seriesId;

    @Schema(description = "0-禁止评论, 1-允许评论")
    private Integer commentEnable;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getExcerpt() { return excerpt; }
    public void setExcerpt(String excerpt) { this.excerpt = excerpt; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getFeaturedImage() { return featuredImage; }
    public void setFeaturedImage(String featuredImage) { this.featuredImage = featuredImage; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public List<Long> getTagIds() { return tagIds; }
    public void setTagIds(List<Long> tagIds) { this.tagIds = tagIds; }
    public Long getSeriesId() { return seriesId; }
    public void setSeriesId(Long seriesId) { this.seriesId = seriesId; }
    public Integer getCommentEnable() { return commentEnable; }
    public void setCommentEnable(Integer commentEnable) { this.commentEnable = commentEnable; }
}
