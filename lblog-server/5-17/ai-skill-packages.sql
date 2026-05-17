-- Skill Package System
-- Run this on the iblog database

CREATE TABLE IF NOT EXISTS ai_skill_packages (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(64) NOT NULL UNIQUE,
    display_name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    keywords VARCHAR(512) NOT NULL,
    modules JSON,
    tools JSON NOT NULL,
    policy JSON,
    is_active TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed draw-expert
INSERT INTO ai_skill_packages (name, display_name, description, keywords, tools)
VALUES ('draw-expert', '绘图专家', 'AI 绘图辅助', '画图,diagram,流程图,ER图,UML',
        '["displayDiagramTool"]')
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), tools = VALUES(tools);

-- Seed chat-general
INSERT INTO ai_skill_packages (name, display_name, description, keywords, tools)
VALUES ('chat-general', '通用对话', '通用 AI 对话', '聊天,问答,帮助',
        '[]')
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), tools = VALUES(tools);
