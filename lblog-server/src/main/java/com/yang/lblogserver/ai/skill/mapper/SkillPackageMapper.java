package com.yang.lblogserver.ai.skill.mapper;

import com.yang.lblogserver.ai.skill.domain.SkillPackage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SkillPackageMapper {

    SkillPackage selectByName(@Param("name") String name);

    List<SkillPackage> selectActive();

    List<SkillPackage> selectActiveByAgent(@Param("agentType") String agentType);

    List<SkillPackage> selectAll();
}
