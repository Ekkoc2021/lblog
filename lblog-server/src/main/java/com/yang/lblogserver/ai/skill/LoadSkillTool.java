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
    private final SkillSystemPromptBuilder promptBuilder;

    public LoadSkillTool(SkillService skillService, SkillSystemPromptBuilder promptBuilder) {
        this.skillService = skillService;
        this.promptBuilder = promptBuilder;
    }

    @Tool(name = "loadSkill", description = """
            Load a skill package to get specialized instructions for a specific domain.
            Call with a skill name to load its instructions into the conversation.
            Call without a name to list available skills.
            """)
    public String loadSkill(
            @org.springframework.ai.tool.annotation.ToolParam(description = "Skill name to load, or empty to list available skills")
            String skillName,
            ToolContext ctx) {
        if (skillName == null || skillName.isBlank()) {
            return listAvailableSkills(ctx);
        }

        var opt = skillService.getSkill(skillName.trim());
        if (opt.isEmpty()) {
            return "Unknown skill: [" + skillName + "].\n\n" + listAvailableSkills(ctx);
        }

        SkillPackage skill = opt.get();
        if (Boolean.FALSE.equals(skill.getIsActive())) {
            return "Skill [" + skillName + "] is currently disabled.";
        }

        String prompt = skill.getPrompt();
        if (prompt == null || prompt.isBlank()) {
            return "Skill [" + skillName + "] loaded. No additional instructions.";
        }

        log.info("Skill loaded: {} ({})", skillName, skill.getDisplayName());
        return "## Skill: " + skill.getDisplayName() + "\n\n" + prompt;
    }

    private String listAvailableSkills(ToolContext ctx) {
        String agentType = ctx != null ? (String) ctx.getContext().get("agentType") : null;
        String hint = promptBuilder.buildAvailableSkillsHint(agentType);
        return hint.isEmpty() ? "No skill packages available." : hint;
    }
}
