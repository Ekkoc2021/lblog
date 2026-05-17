package com.yang.lblogserver.ai.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import jakarta.annotation.PostConstruct;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SkillToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(SkillToolRegistry.class);

    private final Map<String, List<String>> toolSkills = new ConcurrentHashMap<>();
    private final Map<String, List<String>> skillTools = new ConcurrentHashMap<>();
    private final Set<String> alwaysAvailable = ConcurrentHashMap.newKeySet();

    @Autowired
    private ApplicationContext applicationContext;

    @PostConstruct
    public void init() {
        scanBeans(applicationContext);
    }

    private void scanBeans(ApplicationContext ctx) {
        String[] beanNames = ctx.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            Object bean = ctx.getBean(beanName);
            registerBean(bean);
        }
        log.info("SkillToolRegistry initialized: {} skill tools, {} always-available tools",
                toolSkills.size(), alwaysAvailable.size());
    }

    void registerBean(Object bean) {
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        ReflectionUtils.doWithMethods(targetClass, method -> {
            org.springframework.ai.tool.annotation.Tool toolAnn =
                    AnnotatedElementUtils.findMergedAnnotation(method, org.springframework.ai.tool.annotation.Tool.class);
            SkillTool skillAnn =
                    AnnotatedElementUtils.findMergedAnnotation(method, SkillTool.class);

            String toolName = resolveToolName(method, toolAnn);

            if (toolAnn != null && skillAnn != null) {
                List<String> skills = List.of(skillAnn.value());
                toolSkills.put(toolName, skills);
                for (String skill : skills) {
                    skillTools.computeIfAbsent(skill, k -> Collections.synchronizedList(new ArrayList<>())).add(toolName);
                }
                log.debug("Registered skill tool: {} -> skills: {}", toolName, skills);
            } else if (toolAnn != null) {
                alwaysAvailable.add(toolName);
                log.debug("Registered always-available tool: {}", toolName);
            }
        }, method -> {
            org.springframework.ai.tool.annotation.Tool toolAnn =
                    AnnotatedElementUtils.findMergedAnnotation(method, org.springframework.ai.tool.annotation.Tool.class);
            return toolAnn != null;
        });
    }

    public boolean isSkillTool(String toolName) {
        return toolSkills.containsKey(toolName);
    }

    public boolean isAlwaysAvailable(String toolName) {
        return alwaysAvailable.contains(toolName);
    }

    public List<String> getToolsBySkill(String skillName) {
        return skillTools.getOrDefault(skillName, List.of());
    }

    public Set<String> getAlwaysAvailable() {
        return Set.copyOf(alwaysAvailable);
    }

    public Map<String, List<String>> getToolSkills() {
        return Map.copyOf(toolSkills);
    }

    private String resolveToolName(Method method, org.springframework.ai.tool.annotation.Tool toolAnn) {
        if (toolAnn != null) {
            String name = toolAnn.name();
            if (!name.isEmpty()) {
                return name;
            }
        }
        return method.getName();
    }
}
