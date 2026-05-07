package com.yang.lblogserver.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
public class LocalFileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorage.class);

    @Value("${lblog.upload-dir:uploads}")
    private String uploadDir;

    @Override
    public String store(InputStream inputStream, String originalFilename,
                        long contentLength, String contentType) {
        try {
            String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
            Path baseDir = Paths.get(uploadDir, datePath);
            Files.createDirectories(baseDir);

            String ext = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
            String uuid = UUID.randomUUID().toString().toLowerCase();
            String filename = uuid + "." + ext;
            Path filePath = baseDir.resolve(filename);

            Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);

            log.info("File saved locally: {} ({} bytes, {})", filePath, contentLength, contentType);
            return "/uploads/" + datePath + "/" + filename;
        } catch (IOException e) {
            throw new RuntimeException("文件保存失败", e);
        }
    }
}
