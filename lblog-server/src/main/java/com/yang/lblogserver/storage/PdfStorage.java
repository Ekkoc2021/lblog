package com.yang.lblogserver.storage;

import org.springframework.core.io.Resource;
import java.io.IOException;
import java.io.InputStream;

public interface PdfStorage {
    StorageResult store(InputStream stream, String filename, long size, String contentType) throws IOException;
    Resource getFile(String path);
    void delete(String path);
}
