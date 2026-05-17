package com.yang.lblogserver.ai.skill;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SkillSessionManager {

    private final Map<String, Set<String>> sessionSkills = new ConcurrentHashMap<>();

    public void loadSkill(String sessionId, String skillName) {
        sessionSkills.computeIfAbsent(sessionId, k -> Collections.synchronizedSet(new HashSet<>())).add(skillName);
    }

    public Set<String> getLoadedSkills(String sessionId) {
        return sessionSkills.getOrDefault(sessionId, Set.of());
    }

    public boolean isSkillLoaded(String sessionId, String skillName) {
        Set<String> skills = sessionSkills.get(sessionId);
        return skills != null && skills.contains(skillName);
    }

    public void unloadSkill(String sessionId, String skillName) {
        Set<String> skills = sessionSkills.get(sessionId);
        if (skills != null) {
            skills.remove(skillName);
        }
    }

    public void clearSession(String sessionId) {
        sessionSkills.remove(sessionId);
    }
}
