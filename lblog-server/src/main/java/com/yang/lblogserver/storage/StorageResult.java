package com.yang.lblogserver.storage;

public class StorageResult {

    private final String url;
    private final String storagePath;

    public StorageResult(String url, String storagePath) {
        this.url = url;
        this.storagePath = storagePath;
    }

    public String getUrl() {
        return url;
    }

    public String getStoragePath() {
        return storagePath;
    }
}
