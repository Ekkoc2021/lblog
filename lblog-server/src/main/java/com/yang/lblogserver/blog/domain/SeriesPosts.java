package com.yang.lblogserver.blog.domain;


import lombok.Data;

/**
 * 专栏文章关联表
 * @TableName series_posts
 */
@Data
public class SeriesPosts {
    /**
     * 
     */
    private Long seriesId;

    /**
     * 
     */
    private Long postId;

    /**
     * 专栏内排序
     */
    private Integer sortOrder;

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
        SeriesPosts other = (SeriesPosts) that;
        return (this.getSeriesId() == null ? other.getSeriesId() == null : this.getSeriesId().equals(other.getSeriesId()))
            && (this.getPostId() == null ? other.getPostId() == null : this.getPostId().equals(other.getPostId()))
            && (this.getSortOrder() == null ? other.getSortOrder() == null : this.getSortOrder().equals(other.getSortOrder()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getSeriesId() == null) ? 0 : getSeriesId().hashCode());
        result = prime * result + ((getPostId() == null) ? 0 : getPostId().hashCode());
        result = prime * result + ((getSortOrder() == null) ? 0 : getSortOrder().hashCode());
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(" [");
        sb.append("Hash = ").append(hashCode());
        sb.append(", seriesId=").append(seriesId);
        sb.append(", postId=").append(postId);
        sb.append(", sortOrder=").append(sortOrder);
        sb.append("]");
        return sb.toString();
    }
}