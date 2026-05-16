package com.yang.lblogserver.site.vo;

public class SiteConfigVO {
    private String imageBaseUrl;
    private long imageMaxSize;
    private boolean registrationEnabled;
    private boolean aiDrawChatEnabled;

    public SiteConfigVO(String imageBaseUrl, long imageMaxSize, boolean registrationEnabled, boolean aiDrawChatEnabled) {
        this.imageBaseUrl = imageBaseUrl;
        this.imageMaxSize = imageMaxSize;
        this.registrationEnabled = registrationEnabled;
        this.aiDrawChatEnabled = aiDrawChatEnabled;
    }

    public String getImageBaseUrl() { return imageBaseUrl; }
    public long getImageMaxSize() { return imageMaxSize; }
    public boolean isRegistrationEnabled() { return registrationEnabled; }
    public void setRegistrationEnabled(boolean registrationEnabled) { this.registrationEnabled = registrationEnabled; }
    public boolean isAiDrawChatEnabled() { return aiDrawChatEnabled; }
    public void setAiDrawChatEnabled(boolean aiDrawChatEnabled) { this.aiDrawChatEnabled = aiDrawChatEnabled; }
}
