package com.yang.lblogserver.ai.prompt.loader;

import com.yang.lblogserver.ai.prompt.config.AiPromptProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
public class FilePromptLoader {

    private static final Logger log = LoggerFactory.getLogger(FilePromptLoader.class);

    private final ResourcePatternResolver resourceResolver;
    private final AiPromptProperties properties;

    public FilePromptLoader(ResourcePatternResolver resourceResolver, AiPromptProperties properties) {
        this.resourceResolver = resourceResolver;
        this.properties = properties;
    }

    public String load(String module, String promptKey) {
        String base = properties.getFileLocation();
        if (!base.endsWith("/")) base += "/";
        String pattern = base + module + "/" + promptKey + ".md";
        try {
            Resource resource = resourceResolver.getResource(pattern);
            if (resource.exists()) {
                return resource.getContentAsString(StandardCharsets.UTF_8);
            }
            log.warn("Prompt file not found: {}", pattern);
            return null;
        } catch (IOException e) {
            log.error("Failed to load prompt file: {}", pattern, e);
            return null;
        }
    }

    public Map<String, String> loadModule(String module) {
        Map<String, String> result = new HashMap<>();
        String base = properties.getFileLocation();
        if (!base.endsWith("/")) base += "/";
        String pattern = base + module + "/*.md";
        try {
            Resource[] resources = resourceResolver.getResources(pattern);
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename != null && filename.endsWith(".md")) {
                    String key = filename.substring(0, filename.length() - 3);
                    String content = resource.getContentAsString(StandardCharsets.UTF_8);
                    result.put(key, content);
                }
            }
        } catch (IOException e) {
            log.error("Failed to load prompt files for module: {}", module, e);
        }
        return result;
    }
}
