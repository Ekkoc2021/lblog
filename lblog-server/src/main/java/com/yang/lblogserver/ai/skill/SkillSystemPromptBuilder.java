package com.yang.lblogserver.ai.skill;

import com.yang.lblogserver.ai.skill.domain.SkillPackage;
import com.yang.lblogserver.ai.skill.service.SkillService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class SkillSystemPromptBuilder {

    private final SkillService skillService;

    public SkillSystemPromptBuilder(SkillService skillService) {
        this.skillService = skillService;
    }

    /**
     * 构建可用技能提示文本，供调用方拼入 system prompt。
     *
     * @param agentType 按 agent 类型筛选，null 或空则返回全部
     * @return 格式化后的技能列表文本，无可用技能时返回空字符串
     */
    public String buildAvailableSkillsHint(String agentType) {
        List<SkillPackage> skills = (agentType != null && !agentType.isBlank())
                ? skillService.getActiveSkillsByAgent(agentType)
                : skillService.getActiveSkills();

        if (skills.isEmpty()) {
            return "";
        }

        return skills.stream()
                .map(s -> String.format("  - %s: %s（loadSkill(\"%s\")）",
                        s.getDisplayName(),
                        s.getDescription() != null ? s.getDescription() : "",
                        s.getName()))
                .collect(Collectors.joining("\n", "Available skill packages:\n", ""));
    }
}
