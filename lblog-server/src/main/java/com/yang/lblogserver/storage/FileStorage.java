package com.yang.lblogserver.storage;

import java.io.InputStream;

public interface FileStorage {

    String store(InputStream inputStream, String originalFilename, long contentLength, String contentType);

    default String getBaseUrl() {
        return "";
    }
}
