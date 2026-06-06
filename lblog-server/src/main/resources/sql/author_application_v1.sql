-- =====================================================
-- lblog 作者申请功能 v1 — 建表 SQL
-- 执行方式: 在 MySQL 8 中 source 本文件 或逐行执行
-- =====================================================

CREATE TABLE IF NOT EXISTS author_applications (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id         BIGINT       NOT NULL COMMENT '申请人用户ID',
    reason          TEXT         NOT NULL COMMENT '申请理由/自我介绍',
    status          TINYINT      NOT NULL DEFAULT 0 COMMENT '0=待审核 1=通过 2=拒绝 3=需补充',
    feedback        TEXT         NULL     COMMENT '管理员反馈/补充要求',
    reviewed_by     BIGINT       NULL     COMMENT '审核人用户ID',
    reviewed_at     DATETIME     NULL     COMMENT '审核时间',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_id (user_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作者申请表';
