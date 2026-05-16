package com.yang.lblogserver.ai.prompt.mapper;

import com.yang.lblogserver.ai.prompt.domain.AiPromptAudit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AiPromptAuditMapper {

    int insert(AiPromptAudit audit);

    List<AiPromptAudit> selectByPromptId(@Param("promptId") Long promptId);

    List<AiPromptAudit> selectByModuleAndKey(@Param("module") String module,
                                             @Param("promptKey") String promptKey);
}
