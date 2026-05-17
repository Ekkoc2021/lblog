package com.yang.lblogserver.ai.skill.service;

import com.yang.lblogserver.ai.skill.domain.SkillPackage;
import com.yang.lblogserver.ai.skill.mapper.SkillPackageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SkillServiceImpl implements SkillService {

    private static final Logger log = LoggerFactory.getLogger(SkillServiceImpl.class);

    private final SkillPackageMapper mapper;

    public SkillServiceImpl(SkillPackageMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<SkillPackage> getSkill(String name) {
        SkillPackage skill = mapper.selectByName(name);
        return Optional.ofNullable(skill);
    }

    @Override
    public List<SkillPackage> getActiveSkills() {
        return mapper.selectActive();
    }

    @Override
    public List<SkillPackage> getAllSkills() {
        return mapper.selectAll();
    }
}
