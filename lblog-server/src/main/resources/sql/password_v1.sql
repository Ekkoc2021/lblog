-- lblog 密码本功能 v1 — 建表 SQL

CREATE TABLE IF NOT EXISTS passwords (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id             BIGINT NOT NULL COMMENT '用户ID',
    site_name           VARCHAR(100) NOT NULL COMMENT '网站名称',
    site_url            VARCHAR(500) DEFAULT '' COMMENT '网址',
    username            VARCHAR(200) NOT NULL COMMENT '账号',
    encrypted_password  TEXT NOT NULL COMMENT 'AES-256-GCM 加密后的密码',
    note                VARCHAR(500) DEFAULT '' COMMENT '备注',
    is_deleted          TINYINT(1) DEFAULT 0 COMMENT '软删除标记',
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_passwords_user (user_id),
    INDEX idx_passwords_user_deleted (user_id, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='密码本表';
