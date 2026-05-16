package com.yang.lblogserver.ai.prompt.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "lblog.ai.prompt")
public class AiPromptProperties {

    private String fileLocation = "classpath:prompts/";
    private boolean cacheEnabled = true;
    private int cacheMaxSize = 100;
    private int cacheTtlSeconds = 0;

    public String getFileLocation() { return fileLocation; }
    public void setFileLocation(String fileLocation) { this.fileLocation = fileLocation; }
    public boolean isCacheEnabled() { return cacheEnabled; }
    public void setCacheEnabled(boolean cacheEnabled) { this.cacheEnabled = cacheEnabled; }
    public int getCacheMaxSize() { return cacheMaxSize; }
    public void setCacheMaxSize(int cacheMaxSize) { this.cacheMaxSize = cacheMaxSize; }
    public int getCacheTtlSeconds() { return cacheTtlSeconds; }
    public void setCacheTtlSeconds(int cacheTtlSeconds) { this.cacheTtlSeconds = cacheTtlSeconds; }
}
