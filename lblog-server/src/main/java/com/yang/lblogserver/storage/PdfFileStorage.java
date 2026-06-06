package com.yang.lblogserver.storage;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.file.*;

@Component
public class PdfFileStorage implements PdfStorage {

    private final Path basePath;

    public PdfFileStorage(@Value("${pdf.storage.path}") String basePath) {
        this.basePath = Paths.get(basePath);
        try { Files.createDirectories(this.basePath); } catch (IOException e) {
            throw new RuntimeException("Cannot create PDF storage directory", e);
        }
    }

    @Override
    public StorageResult store(InputStream stream, String filename, long size, String contentType) throws IOException {
        Path filePath = basePath.resolve(filename);
        Files.createDirectories(filePath.getParent());
        Files.copy(stream, filePath, StandardCopyOption.REPLACE_EXISTING);
        String url = "/api/v1/pdf/files/" + filename + "/download";
        return new StorageResult(url, filePath.toString());
    }

    @Override
    public Resource getFile(String path) {
        return new FileSystemResource(path);
    }

    @Override
    public void delete(String path) {
        try { Files.deleteIfExists(Paths.get(path)); } catch (IOException ignored) {}
    }
}
