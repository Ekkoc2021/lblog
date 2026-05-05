package com.yang.lblogserver.controller;

import com.yang.lblogserver.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// TODO: ─────────────────────────────────────────────
// TODO: 临时上传实现，后续改 OSS/云存储
// TODO: ─────────────────────────────────────────────
@Tag(name = "上传", description = "图片上传")
@RestController
@RequestMapping("/api/v1/upload")
public class UploadController {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp", "svg");
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    @Value("${lblog.upload-dir:uploads}")
    private String uploadDir;

    @Operation(summary = "上传图片", description = "支持 jpg/png/gif/webp/svg，最大 5MB")
    @PostMapping("/image")
    public ApiResponse<Map<String, String>> uploadImage(@RequestParam(value = "file", required = false) MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ApiResponse.error(400, "请选择要上传的图片");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isEmpty()) {
            return ApiResponse.error(400, "文件名不能为空");
        }

        String ext = originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            return ApiResponse.error(400, "不支持的图片格式，仅支持 jpg/png/gif/webp/svg");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            return ApiResponse.error(400, "图片大小不能超过 5MB");
        }

        try {
            // 按日期分目录: uploads/2026/04/25/
            String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            Path baseDir = Paths.get(uploadDir, datePath);
            Files.createDirectories(baseDir);

            String fileName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
            Path filePath = baseDir.resolve(fileName);
            Files.copy(file.getInputStream(), filePath);

            String url = "/uploads/" + datePath + "/" + fileName;
            return ApiResponse.success(Map.of("url", url));
        } catch (IOException e) {
            // TODO: 改为日志记录
            return ApiResponse.error(500, "上传失败：" + e.getMessage());
        }
    }
}
