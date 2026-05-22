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
     * 构建懒加载提示 — 仅一行，告诉 LLM 有技能可浏览，不枚举具体内容。
     * <p>
     * 用于注入 system prompt，token 开销极小。LLM 需要时自行调 loadSkill() 无参浏览。
     *
     * @param agentType 按 agent 类型筛选，null 或空则返回全部
     * @return 提示文本，无可用技能时返回空字符串
     */
    public String buildLazyHint(String agentType) {
        return "You can call loadSkill() without arguments to browse and load specialized skills on demand.";
    }

    /**
     * 构建完整技能列表（含引导语 + 所有技能），供 loadSkill 无参调用时返回。
     *
     * @param agentType 按 agent 类型筛选，null 或空则返回全部
     * @return 技能列表文本，无可用技能时返回空字符串
     */
    public String buildAvailableSkillsHint(String agentType) {
        List<SkillPackage> skills = resolveSkills(agentType);
        if (skills.isEmpty()) return "";

        String list = skills.stream()
                .map(s -> "loadSkill(\"" + s.getName() + "\") — "
                        + (s.getDescription() != null ? s.getDescription() : ""))
                .collect(Collectors.joining("\n"));

        return list;
    }

    List<SkillPackage> resolveSkills(String agentType) {
        return (agentType != null && !agentType.isBlank())
                ? skillService.getActiveSkillsByAgent(agentType)
                : skillService.getActiveSkills();
    }
}
