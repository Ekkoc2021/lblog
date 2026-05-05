package com.yang.lblogserver.domain;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 用户令牌表
 * @TableName user_tokens
 */
@Data
public class UserToken {
    /**
     *
     */
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * SHA-256(token)
     */
    private String tokenHash;

    /**
     * ACCESS / REFRESH
     */
    private String tokenType;

    /**
     * 过期时间
     */
    private LocalDateTime expiresAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 是否吊销
     */
    private Boolean revoked;

    /**
     * rotation: 被哪个新 token_hash 替换
     */
    private String replacedBy;

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
        UserToken other = (UserToken) that;
        return (this.getId() == null ? other.getId() == null : this.getId().equals(other.getId()))
            && (this.getUserId() == null ? other.getUserId() == null : this.getUserId().equals(other.getUserId()))
            && (this.getTokenHash() == null ? other.getTokenHash() == null : this.getTokenHash().equals(other.getTokenHash()))
            && (this.getTokenType() == null ? other.getTokenType() == null : this.getTokenType().equals(other.getTokenType()))
            && (this.getExpiresAt() == null ? other.getExpiresAt() == null : this.getExpiresAt().equals(other.getExpiresAt()))
            && (this.getCreatedAt() == null ? other.getCreatedAt() == null : this.getCreatedAt().equals(other.getCreatedAt()))
            && (this.getRevoked() == null ? other.getRevoked() == null : this.getRevoked().equals(other.getRevoked()))
            && (this.getReplacedBy() == null ? other.getReplacedBy() == null : this.getReplacedBy().equals(other.getReplacedBy()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getId() == null) ? 0 : getId().hashCode());
        result = prime * result + ((getUserId() == null) ? 0 : getUserId().hashCode());
        result = prime * result + ((getTokenHash() == null) ? 0 : getTokenHash().hashCode());
        result = prime * result + ((getTokenType() == null) ? 0 : getTokenType().hashCode());
        result = prime * result + ((getExpiresAt() == null) ? 0 : getExpiresAt().hashCode());
        result = prime * result + ((getCreatedAt() == null) ? 0 : getCreatedAt().hashCode());
        result = prime * result + ((getRevoked() == null) ? 0 : getRevoked().hashCode());
        result = prime * result + ((getReplacedBy() == null) ? 0 : getReplacedBy().hashCode());
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(" [");
        sb.append("Hash = ").append(hashCode());
        sb.append(", id=").append(id);
        sb.append(", userId=").append(userId);
        sb.append(", tokenHash=").append(tokenHash);
        sb.append(", tokenType=").append(tokenType);
        sb.append(", expiresAt=").append(expiresAt);
        sb.append(", createdAt=").append(createdAt);
        sb.append(", revoked=").append(revoked);
        sb.append(", replacedBy=").append(replacedBy);
        sb.append("]");
        return sb.toString();
    }
}
