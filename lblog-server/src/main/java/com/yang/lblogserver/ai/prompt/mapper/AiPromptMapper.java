package com.yang.lblogserver.ai.prompt.mapper;

import com.yang.lblogserver.ai.prompt.domain.AiPrompt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AiPromptMapper {

    AiPrompt selectById(@Param("id") Long id);

    AiPrompt selectActive(@Param("module") String module, @Param("promptKey") String promptKey);

    List<AiPrompt> selectList(@Param("module") String module,
                              @Param("promptKey") String promptKey,
                              @Param("isActive") Boolean isActive);

    List<AiPrompt> selectByModule(@Param("module") String module);

    List<AiPrompt> selectVersions(@Param("module") String module, @Param("promptKey") String promptKey);

    int insert(AiPrompt prompt);

    int deactivate(@Param("id") Long id);

    int activateVersion(@Param("module") String module,
                        @Param("promptKey") String promptKey,
                        @Param("version") Integer version);

    int countByModuleAndKey(@Param("module") String module, @Param("promptKey") String promptKey);
}

