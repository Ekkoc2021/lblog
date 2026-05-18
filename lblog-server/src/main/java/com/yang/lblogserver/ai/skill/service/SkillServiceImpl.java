package com.yang.lblogserver.ai.skill.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yang.lblogserver.ai.skill.domain.SkillPackage;
import com.yang.lblogserver.ai.skill.mapper.SkillPackageMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class SkillServiceImpl implements SkillService {

    private static final Logger log = LoggerFactory.getLogger(SkillServiceImpl.class);

    private final SkillPackageMapper mapper;
    private Cache<String, List<SkillPackage>> cache;

    public SkillServiceImpl(SkillPackageMapper mapper) {
        this.mapper = mapper;
    }

    @PostConstruct
    public void init() {
        this.cache = Caffeine.newBuilder()
                .maximumSize(50)
                .expireAfterWrite(30, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public Optional<SkillPackage> getSkill(String name) {
        SkillPackage skill = mapper.selectByName(name);
        return Optional.ofNullable(skill);
    }

    @Override
    public List<SkillPackage> getActiveSkills() {
        return getCachedOrLoad("all", mapper::selectActive);
    }

    @Override
    public List<SkillPackage> getActiveSkillsByAgent(String agentType) {
        if (agentType == null || agentType.isBlank()) {
            return getActiveSkills();
        }
        return getCachedOrLoad("agent:" + agentType,
                () -> mapper.selectActiveByAgent(agentType));
    }

    @Override
    public List<SkillPackage> getAllSkills() {
        return mapper.selectAll();
    }

    @SuppressWarnings("unchecked")
    private List<SkillPackage> getCachedOrLoad(String key, java.util.function.Supplier<List<SkillPackage>> loader) {
        List<SkillPackage> cached = cache.getIfPresent(key);
        if (cached != null) return cached;
        List<SkillPackage> result = loader.get();
        if (result != null) {
            cache.put(key, Collections.unmodifiableList(result));
        }
        return result != null ? result : List.of();
    }
}
