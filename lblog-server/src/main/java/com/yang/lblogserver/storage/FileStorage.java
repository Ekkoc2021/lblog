package com.yang.lblogserver.storage;

import java.io.InputStream;

public interface FileStorage {

    StorageResult store(InputStream inputStream, String originalFilename, long contentLength, String contentType);

    /**
     * 根据存储路径删除文件
     * @param storagePath 存储路径（POSIX 风格，由 store() 返回的 storagePath）
     */
    void delete(String storagePath);

    default String getBaseUrl() {
        return "";
    }
}
