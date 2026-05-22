package com.yang.lblogserver.ai.agent.draw;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "lblog.diagram")
public class DrawProperties {

    private boolean enabled = true;
    private int disconnectCheckIntervalSeconds = 5;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getDisconnectCheckIntervalSeconds() { return disconnectCheckIntervalSeconds; }
    public void setDisconnectCheckIntervalSeconds(int disconnectCheckIntervalSeconds) { this.disconnectCheckIntervalSeconds = disconnectCheckIntervalSeconds; }
}
