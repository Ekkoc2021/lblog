package com.yang.lblogserver.ai.agent.draw;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "lblog.diagram")
public class DiagramProperties {

    private boolean enabled = true;
    private String model = "gpt-4o";
    private int maxTokens = 2000;
    private int disconnectCheckIntervalSeconds = 5;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    public int getDisconnectCheckIntervalSeconds() { return disconnectCheckIntervalSeconds; }
    public void setDisconnectCheckIntervalSeconds(int disconnectCheckIntervalSeconds) { this.disconnectCheckIntervalSeconds = disconnectCheckIntervalSeconds; }
}
