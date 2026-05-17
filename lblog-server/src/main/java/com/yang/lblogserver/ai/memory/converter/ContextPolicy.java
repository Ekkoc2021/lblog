package com.yang.lblogserver.ai.memory.converter;

public class ContextPolicy {

    private boolean includeReasoning = false;
    private int maxHistoryTokens = 4000;
    private boolean includeToolResults = true;
    private int recentRounds = 20;

    public boolean isIncludeReasoning() {
        return includeReasoning;
    }

    public void setIncludeReasoning(boolean includeReasoning) {
        this.includeReasoning = includeReasoning;
    }

    public int getMaxHistoryTokens() {
        return maxHistoryTokens;
    }

    public void setMaxHistoryTokens(int maxHistoryTokens) {
        this.maxHistoryTokens = maxHistoryTokens;
    }

    public boolean isIncludeToolResults() {
        return includeToolResults;
    }

    public void setIncludeToolResults(boolean includeToolResults) {
        this.includeToolResults = includeToolResults;
    }

    public int getRecentRounds() {
        return recentRounds;
    }

    public void setRecentRounds(int recentRounds) {
        this.recentRounds = recentRounds;
    }
}
