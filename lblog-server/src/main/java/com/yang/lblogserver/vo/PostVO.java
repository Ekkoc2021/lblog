package com.yang.lblogserver.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Date;
import java.util.List;

@Schema(description = "文章列表项")
public class PostVO {
    @Schema(description = "文章ID")
    private Long id;
    @Schema(description = "文章标题")
    private String title;
    @Schema(description = "URL标识")
    private String slug;
    @Schema(description = "摘要")
    private String excerpt;
    @Schema(description = "特色图片")
    private String featuredImage;
    @Schema(description = "状态: 0-草稿, 1-已发布, 2-私密")
    private Integer status;
    @Schema(description = "作者ID")
    private Long authorId;
    @Schema(description = "分类ID")
    private Long categoryId;
    @Schema(description = "发布时间")
    private Date publishedAt;
    @Schema(description = "创建时间")
    private Date createdAt;
    @Schema(description = "更新时间")
    private Date updatedAt;
    @Schema(description = "浏览量")
    private Integer viewCount;
    @Schema(description = "点赞数")
    private Integer likeCount;
    @Schema(description = "评论数")
    private Integer commentCount;
    @Schema(description = "是否允许评论: 0-不允许, 1-允许")
    private Integer commentEnable;
    @Schema(description = "作者信息")
    private AuthorVO author;
    @Schema(description = "分类信息")
    private CategoryVO category;
    @Schema(description = "标签列表")
    private List<TagVO> tags;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getExcerpt() { return excerpt; }
    public void setExcerpt(String excerpt) { this.excerpt = excerpt; }
    public String getFeaturedImage() { return featuredImage; }
    public void setFeaturedImage(String featuredImage) { this.featuredImage = featuredImage; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public Date getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Date publishedAt) { this.publishedAt = publishedAt; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
    public Integer getViewCount() { return viewCount; }
    public void setViewCount(Integer viewCount) { this.viewCount = viewCount; }
    public Integer getLikeCount() { return likeCount; }
    public void setLikeCount(Integer likeCount) { this.likeCount = likeCount; }
    public Integer getCommentCount() { return commentCount; }
    public void setCommentCount(Integer commentCount) { this.commentCount = commentCount; }
    public Integer getCommentEnable() { return commentEnable; }
    public void setCommentEnable(Integer commentEnable) { this.commentEnable = commentEnable; }
    public AuthorVO getAuthor() { return author; }
    public void setAuthor(AuthorVO author) { this.author = author; }
    public CategoryVO getCategory() { return category; }
    public void setCategory(CategoryVO category) { this.category = category; }
    public List<TagVO> getTags() { return tags; }
    public void setTags(List<TagVO> tags) { this.tags = tags; }

    @Schema(description = "作者信息")
    public static class AuthorVO {
        @Schema(description = "作者ID")
        private Long id;
        @Schema(description = "作者昵称")
        private String nickname;
        @Schema(description = "头像URL")
        private String avatar;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public String getAvatar() { return avatar; }
        public void setAvatar(String avatar) { this.avatar = avatar; }
    }
}
