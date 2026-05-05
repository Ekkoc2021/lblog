CREATE TABLE user_tokens (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL COMMENT '用户ID',
    token_hash  VARCHAR(64)  NOT NULL COMMENT 'SHA-256(token)',
    token_type  VARCHAR(10)  NOT NULL COMMENT 'ACCESS / REFRESH',
    expires_at  DATETIME     NOT NULL COMMENT '过期时间',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    revoked     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否吊销',
    replaced_by VARCHAR(64)  DEFAULT NULL COMMENT 'rotation: 被哪个新 token_hash 替换',
    CONSTRAINT fk_token_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户令牌表';

CREATE UNIQUE INDEX uk_token_hash ON user_tokens(token_hash);
CREATE INDEX idx_user_id ON user_tokens(user_id);
CREATE INDEX idx_expires ON user_tokens(expires_at);
