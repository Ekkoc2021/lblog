-- =====================================================
-- lblog 代办功能 v1 — 建表 SQL
-- 执行方式: 在 MySQL 8 中 source 本文件 或逐行执行
-- =====================================================

CREATE TABLE IF NOT EXISTS todos (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id       BIGINT NOT NULL COMMENT '用户ID',
    title         VARCHAR(500) NOT NULL COMMENT '代办标题',
    note          TEXT COMMENT '备注说明',
    priority      TINYINT DEFAULT 0 COMMENT '优先级',
    status        TINYINT DEFAULT 0 COMMENT '状态',
    due_date      DATE COMMENT '截止日期',
    sort_order    INT DEFAULT 0 COMMENT '排序序号',
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_todos_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代办表';

CREATE TABLE IF NOT EXISTS todo_tags (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id       BIGINT NOT NULL COMMENT '用户ID',
    name          VARCHAR(50) NOT NULL COMMENT '标签名称',
    UNIQUE KEY uk_user_tag (user_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代办标签表';

CREATE TABLE IF NOT EXISTS todo_tag_relations (
    todo_id       BIGINT NOT NULL COMMENT '代办ID',
    tag_id        BIGINT NOT NULL COMMENT '标签ID',
    PRIMARY KEY (todo_id, tag_id),
    FOREIGN KEY (todo_id) REFERENCES todos(id) ON DELETE CASCADE,
    FOREIGN KEY (tag_id) REFERENCES todo_tags(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代办-标签关联表';

CREATE TABLE IF NOT EXISTS todo_items (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    todo_id       BIGINT NOT NULL COMMENT '所属代办ID',
    title         VARCHAR(500) NOT NULL COMMENT '子项标题',
    completed     TINYINT(1) DEFAULT 0 COMMENT '是否完成',
    sort_order    INT DEFAULT 0 COMMENT '排序序号',
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    FOREIGN KEY (todo_id) REFERENCES todos(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代办子项表';
