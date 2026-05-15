package com.yang.lblogserver.blog.vo.admin;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "站点统计数据")
public class StatisticsVO {
    @Schema(description = "文章总数")
    private long postCount;
    @Schema(description = "总浏览量")
    private long viewCount;
    @Schema(description = "总点赞数")
    private long likeCount;
    @Schema(description = "总评论数")
    private long commentCount;
    @Schema(description = "分类分布")
    private List<CategoryDist> categoryDistribution;
    @Schema(description = "标签分布")
    private List<TagDist> tagDistribution;

    public long getPostCount() { return postCount; }
    public void setPostCount(long postCount) { this.postCount = postCount; }
    public long getViewCount() { return viewCount; }
    public void setViewCount(long viewCount) { this.viewCount = viewCount; }
    public long getLikeCount() { return likeCount; }
    public void setLikeCount(long likeCount) { this.likeCount = likeCount; }
    public long getCommentCount() { return commentCount; }
    public void setCommentCount(long commentCount) { this.commentCount = commentCount; }
    public List<CategoryDist> getCategoryDistribution() { return categoryDistribution; }
    public void setCategoryDistribution(List<CategoryDist> categoryDistribution) { this.categoryDistribution = categoryDistribution; }
    public List<TagDist> getTagDistribution() { return tagDistribution; }
    public void setTagDistribution(List<TagDist> tagDistribution) { this.tagDistribution = tagDistribution; }

    @Schema(description = "分类分布")
    public static class CategoryDist {
        private String name;
        private long count;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public long getCount() { return count; }
        public void setCount(long count) { this.count = count; }
    }

    @Schema(description = "标签分布")
    public static class TagDist {
        private String name;
        private long count;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public long getCount() { return count; }
        public void setCount(long count) { this.count = count; }
    }
}
