package com.yang.lblogserver.vo;

public class SiteConfigVO {
    private String imageBaseUrl;
    private long imageMaxSize;

    public SiteConfigVO(String imageBaseUrl, long imageMaxSize) {
        this.imageBaseUrl = imageBaseUrl;
        this.imageMaxSize = imageMaxSize;
    }

    public String getImageBaseUrl() { return imageBaseUrl; }
    public long getImageMaxSize() { return imageMaxSize; }
}
