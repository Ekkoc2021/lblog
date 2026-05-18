package com.yang.lblogserver.ai.skill;

import com.yang.lblogserver.ai.skill.domain.SkillPackage;
import com.yang.lblogserver.ai.skill.service.SkillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class LoadSkillTool {

    private static final Logger log = LoggerFactory.getLogger(LoadSkillTool.class);

    private final SkillService skillService;

    public LoadSkillTool(SkillService skillService) {
        this.skillService = skillService;
    }

    @Tool(name = "loadSkill", description = """
            Load a skill package to acquire domain-specific expertise.
            Each skill package contains specialized instructions that modify how you respond.
            Call without arguments to list available skill packages for your agent.
            """)
    public String loadSkill(String skillName, ToolContext ctx) {
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
        List<SkillPackage> skills = (agentType != null && !agentType.isBlank())
                ? skillService.getActiveSkillsByAgent(agentType)
                : skillService.getActiveSkills();
        if (skills.isEmpty()) {
            return "No skill packages available.";
        }
        return skills.stream()
                .map(s -> String.format("| %-20s | %s", s.getName(),
                        s.getDescription() != null ? s.getDescription() : ""))
                .collect(Collectors.joining("\n",
                        "Available skill packages:\n\n"
                        + "| Name                 | Description\n"
                        + "|----------------------|------------\n", ""));
    }
}
