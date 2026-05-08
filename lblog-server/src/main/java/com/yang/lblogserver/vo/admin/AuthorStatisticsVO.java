package com.yang.lblogserver.vo.admin;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "作者个人统计数据")
public class AuthorStatisticsVO {

    @Schema(description = "文章总数")
    private long totalPosts;
    @Schema(description = "总浏览量")
    private long totalViews;
    @Schema(description = "总点赞数")
    private long totalLikes;
    @Schema(description = "总评论数")
    private long totalComments;
    @Schema(description = "状态分布")
    private List<StatusItem> statusDistribution;
    @Schema(description = "分类分布")
    private List<CategoryItem> categoryDistribution;
    @Schema(description = "月度发文趋势")
    private List<MonthItem> monthlyTrend;

    public long getTotalPosts() { return totalPosts; }
    public void setTotalPosts(long totalPosts) { this.totalPosts = totalPosts; }
    public long getTotalViews() { return totalViews; }
    public void setTotalViews(long totalViews) { this.totalViews = totalViews; }
    public long getTotalLikes() { return totalLikes; }
    public void setTotalLikes(long totalLikes) { this.totalLikes = totalLikes; }
    public long getTotalComments() { return totalComments; }
    public void setTotalComments(long totalComments) { this.totalComments = totalComments; }
    public List<StatusItem> getStatusDistribution() { return statusDistribution; }
    public void setStatusDistribution(List<StatusItem> statusDistribution) { this.statusDistribution = statusDistribution; }
    public List<CategoryItem> getCategoryDistribution() { return categoryDistribution; }
    public void setCategoryDistribution(List<CategoryItem> categoryDistribution) { this.categoryDistribution = categoryDistribution; }
    public List<MonthItem> getMonthlyTrend() { return monthlyTrend; }
    public void setMonthlyTrend(List<MonthItem> monthlyTrend) { this.monthlyTrend = monthlyTrend; }

    @Schema(description = "状态分布项")
    public static class StatusItem {
        @Schema(description = "状态：0-草稿 1-已发布 2-私密")
        private int status;
        @Schema(description = "数量")
        private int count;

        public int getStatus() { return status; }
        public void setStatus(int status) { this.status = status; }
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
    }

    @Schema(description = "分类分布项")
    public static class CategoryItem {
        @Schema(description = "分类名称")
        private String categoryName;
        @Schema(description = "分类别名")
        private String categorySlug;
        @Schema(description = "文章数量")
        private int postCount;

        public String getCategoryName() { return categoryName; }
        public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
        public String getCategorySlug() { return categorySlug; }
        public void setCategorySlug(String categorySlug) { this.categorySlug = categorySlug; }
        public int getPostCount() { return postCount; }
        public void setPostCount(int postCount) { this.postCount = postCount; }
    }

    @Schema(description = "月度趋势项")
    public static class MonthItem {
        @Schema(description = "月份，格式 YYYY-MM")
        private String month;
        @Schema(description = "发文数量")
        private int count;

        public String getMonth() { return month; }
        public void setMonth(String month) { this.month = month; }
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
    }
}
