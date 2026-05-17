package com.yang.lblogserver.ai.skill;

import com.yang.lblogserver.ai.skill.domain.SkillPackage;
import com.yang.lblogserver.ai.skill.service.SkillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LoadSkillTool {

    private static final Logger log = LoggerFactory.getLogger(LoadSkillTool.class);

    private final SkillService skillService;
    private final SkillToolRegistry skillToolRegistry;
    private final SkillSessionManager skillSessionManager;

    public LoadSkillTool(SkillService skillService,
                         SkillToolRegistry skillToolRegistry,
                         SkillSessionManager skillSessionManager) {
        this.skillService = skillService;
        this.skillToolRegistry = skillToolRegistry;
        this.skillSessionManager = skillSessionManager;
    }

    @Tool(name = "loadSkill", description = "加载指定技能包及其工具。可用技能: draw-expert, chat-general。技能加载后其工具会变得可用。")
    public String loadSkill(String skillName, ToolContext ctx) {
        var opt = skillService.getSkill(skillName);
        if (opt.isEmpty()) {
            return "技能 '" + skillName + "' 不存在。可用技能: draw-expert, chat-general";
        }

        SkillPackage skill = opt.get();
        if (Boolean.FALSE.equals(skill.getIsActive())) {
            return "技能 '" + skillName + "' 已停用";
        }

        String sessionId = (String) ctx.getContext().get("sessionId");
        if (sessionId != null) {
            skillSessionManager.loadSkill(sessionId, skillName);
        }

        List<String> tools = skillToolRegistry.getToolsBySkill(skillName);
        log.info("Loaded skill: {}, tools: {}", skillName, tools);

        if (tools.isEmpty()) {
            return "技能 '" + skillName + "' 已加载，该技能没有额外工具。";
        }

        return "技能 '" + skillName + "' 已加载。可用工具: " + String.join(", ", tools);
    }
}
