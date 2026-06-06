package com.yang.lblogserver.auth.domain;

import java.util.Date;

public class PdfUserQuota {
    private Long id;
    private Long userId;
    private Long quotaBytes;
    private Integer allowUpload;
    private Date createdAt;
    private Date updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getQuotaBytes() { return quotaBytes; }
    public void setQuotaBytes(Long quotaBytes) { this.quotaBytes = quotaBytes; }
    public Integer getAllowUpload() { return allowUpload; }
    public void setAllowUpload(Integer allowUpload) { this.allowUpload = allowUpload; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
