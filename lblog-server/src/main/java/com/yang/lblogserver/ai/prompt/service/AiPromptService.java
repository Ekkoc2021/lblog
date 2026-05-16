package com.yang.lblogserver.ai.prompt.service;

import com.yang.lblogserver.ai.prompt.domain.AiPrompt;

import java.util.List;
import java.util.Map;

public interface AiPromptService {

    String getPrompt(String module, String promptKey);

    List<AiPrompt> getPrompts(String module);

    Map<String, String> getPromptMap(String module);

    AiPrompt getPromptById(Long id);

    AiPrompt createPrompt(AiPrompt prompt);

    AiPrompt updatePrompt(Long id, String newContent, String operator);

    AiPrompt updatePromptMeta(Long id, String description, Integer sortOrder, String operator);

    void deactivatePrompt(Long id, String operator);

    void reloadCache();

    int seedFromFiles(String module);
}
