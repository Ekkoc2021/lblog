-- Skill Package System (Simplified — skill = prompt)
-- Run this on the iblog database

ALTER TABLE ai_skill_packages
  DROP COLUMN modules,
  DROP COLUMN tools,
  DROP COLUMN policy,
  ADD COLUMN prompt TEXT NOT NULL COMMENT '技能提示词，loadSkill 返回此内容' AFTER description;

-- Seed draw-expert（绘图 Agent 专用）
INSERT INTO ai_skill_packages (name, agent_type, display_name, description, prompt, keywords, is_active)
VALUES ('draw-expert', 'draw', '绘图专家', 'AI 绘图辅助，帮助用户创建各种图表',
        'You are a professional diagram designer skilled in creating various types of diagrams. ' ||
        'When creating diagrams:\n' ||
        '1. Analyze the user request to determine the most suitable diagram type (flowchart, ERD, UML, mind map, etc.)\n' ||
        '2. Plan the diagram structure including all necessary components\n' ||
        '3. Generate valid draw.io XML using the display_diagram tool\n' ||
        '4. Explain the diagram briefly so the user understands what was created',
        '画图,diagram,flowchart,流程图,ER图,UML,思维导图,mindmap', 1)
ON DUPLICATE KEY UPDATE
  display_name = VALUES(display_name),
  description = VALUES(description),
  prompt = VALUES(prompt),
  keywords = VALUES(keywords);

-- Seed chat-general（通用对话 Agent 专用）
INSERT INTO ai_skill_packages (name, agent_type, display_name, description, prompt, keywords, is_active)
VALUES ('chat-general', 'chat', '通用对话', '通用 AI 对话助手',
        'You are a helpful AI assistant. Engage in natural conversation, answer questions, and help the user with their requests. ' ||
        'Be concise, accurate, and friendly.',
        '聊天,问答,帮助,conversation,Q&A', 1)
ON DUPLICATE KEY UPDATE
  display_name = VALUES(display_name),
  description = VALUES(description),
  prompt = VALUES(prompt),
  keywords = VALUES(keywords);
