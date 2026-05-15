package com.yang.lblogserver.blog.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;
import java.util.List;

@Schema(description = "评论")
public class CommentVO {

    @Schema(description = "评论 ID")
    private Long id;
    @Schema(description = "作者")
    private AuthorVO author;
    @Schema(description = "回复目标")
    private ReplyToVO replyTo;
    @Schema(description = "正文")
    private String content;
    @Schema(description = "点赞数")
    private Integer likeCount;
    @Schema(description = "回复数（仅顶级评论返回）")
    private Integer replyCount;
    @Schema(description = "创建时间")
    private Date createdAt;
    @Schema(description = "子回复（加载后填充）")
    private List<CommentVO> replies;

    @Schema(description = "作者信息")
    public static class AuthorVO {
        private Long id;
        private String nickname;
        private String avatar;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public String getAvatar() { return avatar; }
        public void setAvatar(String avatar) { this.avatar = avatar; }
    }

    @Schema(description = "回复目标")
    public static class ReplyToVO {
        private Long id;
        private String nickname;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
    }

    // admin
    @Schema(description = "所属文章 ID")
    private Long postId;
    @Schema(description = "审核状态 0=待审 1=通过 2=驳回")
    private Integer status;
    @Schema(description = "IP 地址")
    private String ipAddress;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public AuthorVO getAuthor() { return author; }
    public void setAuthor(AuthorVO author) { this.author = author; }
    public ReplyToVO getReplyTo() { return replyTo; }
    public void setReplyTo(ReplyToVO replyTo) { this.replyTo = replyTo; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getLikeCount() { return likeCount; }
    public void setLikeCount(Integer likeCount) { this.likeCount = likeCount; }
    public Integer getReplyCount() { return replyCount; }
    public void setReplyCount(Integer replyCount) { this.replyCount = replyCount; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public List<CommentVO> getReplies() { return replies; }
    public void setReplies(List<CommentVO> replies) { this.replies = replies; }
    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
}
