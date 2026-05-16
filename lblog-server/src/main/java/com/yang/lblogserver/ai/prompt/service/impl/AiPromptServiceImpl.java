package com.yang.lblogserver.ai.prompt.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yang.lblogserver.ai.prompt.config.AiPromptProperties;
import com.yang.lblogserver.ai.prompt.domain.AiPrompt;
import com.yang.lblogserver.ai.prompt.domain.AiPromptAudit;
import com.yang.lblogserver.ai.prompt.loader.FilePromptLoader;
import com.yang.lblogserver.ai.prompt.mapper.AiPromptAuditMapper;
import com.yang.lblogserver.ai.prompt.mapper.AiPromptMapper;
import com.yang.lblogserver.ai.prompt.service.AiPromptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class AiPromptServiceImpl implements AiPromptService {

    private static final Logger log = LoggerFactory.getLogger(AiPromptServiceImpl.class);

    private final AiPromptMapper promptMapper;
    private final AiPromptAuditMapper auditMapper;
    private final FilePromptLoader filePromptLoader;
    private final AiPromptProperties properties;

    private final Cache<String, String> cache;

    public AiPromptServiceImpl(AiPromptMapper promptMapper,
                               AiPromptAuditMapper auditMapper,
                               FilePromptLoader filePromptLoader,
                               AiPromptProperties properties) {
        this.promptMapper = promptMapper;
        this.auditMapper = auditMapper;
        this.filePromptLoader = filePromptLoader;
        this.properties = properties;

        Caffeine<Object, Object> builder = Caffeine.newBuilder().maximumSize(properties.getCacheMaxSize());
        if (properties.getCacheTtlSeconds() > 0) {
            builder.expireAfterWrite(properties.getCacheTtlSeconds(), TimeUnit.SECONDS);
        }
        this.cache = properties.isCacheEnabled() ? builder.build() : null;
    }

    @Override
    public String getPrompt(String module, String promptKey) {
        String cacheKey = module + ":" + promptKey;
        if (cache != null) {
            String cached = cache.getIfPresent(cacheKey);
            if (cached != null) return cached;
        }

        AiPrompt db = promptMapper.selectActive(module, promptKey);
        if (db != null) {
            if (cache != null) cache.put(cacheKey, db.getContent());
            return db.getContent();
        }

        String file = filePromptLoader.load(module, promptKey);
        if (file != null) {
            if (cache != null) cache.put(cacheKey, file);
            return file;
        }

        log.warn("Prompt not found: module={}, key={}, returning empty", module, promptKey);
        return "";
    }

    @Override
    public List<AiPrompt> getPrompts(String module) {
        return promptMapper.selectByModule(module);
    }

    @Override
    public Map<String, String> getPromptMap(String module) {
        List<AiPrompt> list = getPrompts(module);

        if (list.isEmpty()) {
            Map<String, String> filePrompts = filePromptLoader.loadModule(module);
            if (!filePrompts.isEmpty()) {
                if (cache != null) {
                    filePrompts.forEach((k, v) -> cache.put(module + ":" + k, v));
                }
                return filePrompts;
            }
            return Collections.emptyMap();
        }

        Map<String, String> result = new LinkedHashMap<>();
        for (AiPrompt p : list) {
            result.put(p.getPromptKey(), p.getContent());
        }
        return result;
    }

    @Override
    public AiPrompt getPromptById(Long id) {
        return promptMapper.selectById(id);
    }

    @Override
    @Transactional
    public AiPrompt createPrompt(AiPrompt prompt) {
        int count = promptMapper.countByModuleAndKey(prompt.getModule(), prompt.getPromptKey());
        prompt.setVersion(count + 1);
        if (prompt.getSortOrder() == null) prompt.setSortOrder(0);
        if (prompt.getIsActive() == null) prompt.setIsActive(true);
        promptMapper.insert(prompt);

        AiPromptAudit audit = new AiPromptAudit();
        audit.setPromptId(prompt.getId());
        audit.setModule(prompt.getModule());
        audit.setPromptKey(prompt.getPromptKey());
        audit.setNewContent(prompt.getContent());
        audit.setNewVersion(prompt.getVersion());
        audit.setAction("CREATE");
        audit.setOperator(prompt.getCreatedBy());
        auditMapper.insert(audit);

        evictCache(prompt.getModule(), prompt.getPromptKey());
        return prompt;
    }

    @Override
    @Transactional
    public AiPrompt updatePrompt(Long id, String newContent, String operator) {
        AiPrompt existing = promptMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("Prompt not found: id=" + id);
        }

        int nextVersion = existing.getVersion() + 1;

        String oldContent = existing.getContent();

        promptMapper.deactivate(id);

        AiPrompt newPrompt = new AiPrompt();
        newPrompt.setModule(existing.getModule());
        newPrompt.setPromptKey(existing.getPromptKey());
        newPrompt.setContent(newContent);
        newPrompt.setSortOrder(existing.getSortOrder());
        newPrompt.setDescription(existing.getDescription());
        newPrompt.setVersion(nextVersion);
        newPrompt.setIsActive(true);
        newPrompt.setCreatedBy(operator);
        newPrompt.setUpdatedBy(operator);
        promptMapper.insert(newPrompt);

        AiPromptAudit audit = new AiPromptAudit();
        audit.setPromptId(newPrompt.getId());
        audit.setModule(existing.getModule());
        audit.setPromptKey(existing.getPromptKey());
        audit.setOldContent(oldContent);
        audit.setNewContent(newContent);
        audit.setOldVersion(existing.getVersion());
        audit.setNewVersion(nextVersion);
        audit.setAction("UPDATE");
        audit.setOperator(operator);
        auditMapper.insert(audit);

        evictCache(existing.getModule(), existing.getPromptKey());
        return newPrompt;
    }

    @Override
    @Transactional
    public AiPrompt updatePromptMeta(Long id, String description, Integer sortOrder, String operator) {
        AiPrompt existing = promptMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("Prompt not found: id=" + id);
        }

        int nextVersion = existing.getVersion() + 1;

        promptMapper.deactivate(id);

        AiPrompt newPrompt = new AiPrompt();
        newPrompt.setModule(existing.getModule());
        newPrompt.setPromptKey(existing.getPromptKey());
        newPrompt.setContent(existing.getContent());
        newPrompt.setSortOrder(sortOrder != null ? sortOrder : existing.getSortOrder());
        newPrompt.setDescription(description != null ? description : existing.getDescription());
        newPrompt.setVersion(nextVersion);
        newPrompt.setIsActive(true);
        newPrompt.setCreatedBy(operator);
        newPrompt.setUpdatedBy(operator);
        promptMapper.insert(newPrompt);

        AiPromptAudit audit = new AiPromptAudit();
        audit.setPromptId(newPrompt.getId());
        audit.setModule(existing.getModule());
        audit.setPromptKey(existing.getPromptKey());
        audit.setOldVersion(existing.getVersion());
        audit.setNewVersion(nextVersion);
        audit.setAction("UPDATE_META");
        audit.setOperator(operator);
        auditMapper.insert(audit);

        evictCache(existing.getModule(), existing.getPromptKey());
        return newPrompt;
    }

    @Override
    @Transactional
    public void deactivatePrompt(Long id, String operator) {
        AiPrompt existing = promptMapper.selectById(id);
        if (existing == null) return;

        promptMapper.deactivate(id);

        AiPromptAudit audit = new AiPromptAudit();
        audit.setPromptId(id);
        audit.setModule(existing.getModule());
        audit.setPromptKey(existing.getPromptKey());
        audit.setOldVersion(existing.getVersion());
        audit.setAction("DEACTIVATE");
        audit.setOperator(operator);
        auditMapper.insert(audit);

        evictCache(existing.getModule(), existing.getPromptKey());
    }

    @Override
    public void reloadCache() {
        if (cache != null) {
            cache.invalidateAll();
            log.info("Prompt cache cleared");
        }
    }

    @Override
    @Transactional
    public int seedFromFiles(String module) {
        Map<String, String> filePrompts = filePromptLoader.loadModule(module);
        int count = 0;
        for (Map.Entry<String, String> entry : filePrompts.entrySet()) {
            String key = entry.getKey();
            if (promptMapper.countByModuleAndKey(module, key) > 0) {
                log.info("Skipping existing prompt: module={}, key={}", module, key);
                continue;
            }

            AiPrompt prompt = new AiPrompt();
            prompt.setModule(module);
            prompt.setPromptKey(key);
            prompt.setContent(entry.getValue());
            prompt.setVersion(1);
            prompt.setSortOrder(count * 10 + 10);
            prompt.setIsActive(true);
            prompt.setCreatedBy("system");
            prompt.setUpdatedBy("system");
            promptMapper.insert(prompt);

            AiPromptAudit audit = new AiPromptAudit();
            audit.setPromptId(prompt.getId());
            audit.setModule(module);
            audit.setPromptKey(key);
            audit.setNewContent(entry.getValue());
            audit.setNewVersion(1);
            audit.setAction("SEED");
            audit.setOperator("system");
            auditMapper.insert(audit);

            count++;
        }
        log.info("Seeded {} prompts for module: {}", count, module);
        evictCache(module, null);
        return count;
    }

    private void evictCache(String module, String promptKey) {
        if (cache == null) return;
        if (promptKey != null) {
            cache.invalidate(module + ":" + promptKey);
        }
    }
}
