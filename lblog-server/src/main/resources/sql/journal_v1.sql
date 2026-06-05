CREATE TABLE IF NOT EXISTS journals (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id        BIGINT NOT NULL COMMENT '用户ID',
    title          VARCHAR(200) DEFAULT '' COMMENT '标题',
    content        TEXT COMMENT '正文',
    mood           VARCHAR(50) DEFAULT '' COMMENT '心情标签',
    mood_emoji     VARCHAR(10) DEFAULT '' COMMENT '心情emoji',
    weather        VARCHAR(20) DEFAULT '' COMMENT '天气',
    journal_date   DATE NOT NULL COMMENT '日记日期',
    is_deleted     TINYINT(1) DEFAULT 0 COMMENT '软删除',
    created_at     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_user_date (user_id, journal_date),
    INDEX idx_journals_user (user_id),
    INDEX idx_journals_user_month (user_id, journal_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日记表';
