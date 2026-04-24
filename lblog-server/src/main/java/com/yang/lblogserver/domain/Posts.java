package com.yang.lblogserver.domain;


import java.util.Date;
import lombok.Data;

/**
 * 文章元数据表
 * @TableName posts
 */

@Data
public class Posts {
    /**
     * 
     */
    private Long id;

    /**
     * 文章标题
     */
    private String title;

    /**
     * URL标识
     */
    private String slug;

    /**
     * 摘要
     */
    private String excerpt;

    /**
     * 特色图片
     */
    private String featuredImage;

    /**
     * 0-草稿，1-已发布，2-私密
     */
    private Integer status;

    /**
     * 作者用户ID
     */
    private Long authorId;

    /**
     * 所属分类ID
     */
    private Long categoryId;

    /**
     * 发布时间
     */
    private Date publishedAt;

    /**
     * 
     */
    private Date createdAt;

    /**
     * 
     */
    private Date updatedAt;

    /**
     * 软删除时间
     */
    private Date deletedAt;

    /**
     * 是否删除
     */
    private Integer isDelelte;

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        }
        if (that == null) {
            return false;
        }
        if (getClass() != that.getClass()) {
            return false;
        }
        Posts other = (Posts) that;
        return (this.getId() == null ? other.getId() == null : this.getId().equals(other.getId()))
            && (this.getTitle() == null ? other.getTitle() == null : this.getTitle().equals(other.getTitle()))
            && (this.getSlug() == null ? other.getSlug() == null : this.getSlug().equals(other.getSlug()))
            && (this.getExcerpt() == null ? other.getExcerpt() == null : this.getExcerpt().equals(other.getExcerpt()))
            && (this.getFeaturedImage() == null ? other.getFeaturedImage() == null : this.getFeaturedImage().equals(other.getFeaturedImage()))
            && (this.getStatus() == null ? other.getStatus() == null : this.getStatus().equals(other.getStatus()))
            && (this.getAuthorId() == null ? other.getAuthorId() == null : this.getAuthorId().equals(other.getAuthorId()))
            && (this.getCategoryId() == null ? other.getCategoryId() == null : this.getCategoryId().equals(other.getCategoryId()))
            && (this.getPublishedAt() == null ? other.getPublishedAt() == null : this.getPublishedAt().equals(other.getPublishedAt()))
            && (this.getCreatedAt() == null ? other.getCreatedAt() == null : this.getCreatedAt().equals(other.getCreatedAt()))
            && (this.getUpdatedAt() == null ? other.getUpdatedAt() == null : this.getUpdatedAt().equals(other.getUpdatedAt()))
            && (this.getDeletedAt() == null ? other.getDeletedAt() == null : this.getDeletedAt().equals(other.getDeletedAt()))
            && (this.getIsDelelte() == null ? other.getIsDelelte() == null : this.getIsDelelte().equals(other.getIsDelelte()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getId() == null) ? 0 : getId().hashCode());
        result = prime * result + ((getTitle() == null) ? 0 : getTitle().hashCode());
        result = prime * result + ((getSlug() == null) ? 0 : getSlug().hashCode());
        result = prime * result + ((getExcerpt() == null) ? 0 : getExcerpt().hashCode());
        result = prime * result + ((getFeaturedImage() == null) ? 0 : getFeaturedImage().hashCode());
        result = prime * result + ((getStatus() == null) ? 0 : getStatus().hashCode());
        result = prime * result + ((getAuthorId() == null) ? 0 : getAuthorId().hashCode());
        result = prime * result + ((getCategoryId() == null) ? 0 : getCategoryId().hashCode());
        result = prime * result + ((getPublishedAt() == null) ? 0 : getPublishedAt().hashCode());
        result = prime * result + ((getCreatedAt() == null) ? 0 : getCreatedAt().hashCode());
        result = prime * result + ((getUpdatedAt() == null) ? 0 : getUpdatedAt().hashCode());
        result = prime * result + ((getDeletedAt() == null) ? 0 : getDeletedAt().hashCode());
        result = prime * result + ((getIsDelelte() == null) ? 0 : getIsDelelte().hashCode());
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(" [");
        sb.append("Hash = ").append(hashCode());
        sb.append(", id=").append(id);
        sb.append(", title=").append(title);
        sb.append(", slug=").append(slug);
        sb.append(", excerpt=").append(excerpt);
        sb.append(", featuredImage=").append(featuredImage);
        sb.append(", status=").append(status);
        sb.append(", authorId=").append(authorId);
        sb.append(", categoryId=").append(categoryId);
        sb.append(", publishedAt=").append(publishedAt);
        sb.append(", createdAt=").append(createdAt);
        sb.append(", updatedAt=").append(updatedAt);
        sb.append(", deletedAt=").append(deletedAt);
        sb.append(", isDelelte=").append(isDelelte);
        sb.append("]");
        return sb.toString();
    }
}