package com.yang.lblogserver.ai.skill.service;

import com.yang.lblogserver.ai.skill.domain.SkillPackage;
import java.util.List;
import java.util.Optional;

public interface SkillService {

    Optional<SkillPackage> getSkill(String name);

    List<SkillPackage> getActiveSkills();

    List<SkillPackage> getAllSkills();
}
