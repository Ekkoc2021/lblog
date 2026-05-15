package com.yang.lblogserver.site.vo;

public class SiteConfigVO {
    private String imageBaseUrl;
    private long imageMaxSize;
    private boolean registrationEnabled;

    public SiteConfigVO(String imageBaseUrl, long imageMaxSize, boolean registrationEnabled) {
        this.imageBaseUrl = imageBaseUrl;
        this.imageMaxSize = imageMaxSize;
        this.registrationEnabled = registrationEnabled;
    }

    public String getImageBaseUrl() { return imageBaseUrl; }
    public long getImageMaxSize() { return imageMaxSize; }
    public boolean isRegistrationEnabled() { return registrationEnabled; }
    public void setRegistrationEnabled(boolean registrationEnabled) { this.registrationEnabled = registrationEnabled; }
}
