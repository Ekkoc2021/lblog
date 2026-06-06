package com.yang.lblogserver.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;

@Schema(description = "申请记录视图")
public class ApplicationVO {

    @Schema(description = "申请ID")
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "用户昵称")
    private String nickname;

    @Schema(description = "申请理由/自我介绍")
    private String reason;

    @Schema(description = "审核状态：0=待审核 1=通过 2=拒绝 3=需补充")
    private Integer status;

    @Schema(description = "审核反馈")
    private String feedback;

    @Schema(description = "审核人ID")
    private Long reviewedBy;

    @Schema(description = "审核时间")
    private Date reviewedAt;

    @Schema(description = "申请时间")
    private Date createdAt;

    @Schema(description = "更新时间")
    private Date updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }
    public Long getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(Long reviewedBy) { this.reviewedBy = reviewedBy; }
    public Date getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Date reviewedAt) { this.reviewedAt = reviewedAt; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
