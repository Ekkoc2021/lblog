package com.yang.lblogserver.controller;

import com.yang.lblogserver.common.ApiResponse;
import com.yang.lblogserver.storage.FileStorage;
import com.yang.lblogserver.vo.UploadImageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

@Tag(name = "上传", description = "图片上传")
@RestController
@RequestMapping("/api/v1/upload")
public class UploadController {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp", "svg");
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    private final FileStorage fileStorage;

    public UploadController(FileStorage fileStorage) {
        this.fileStorage = fileStorage;
    }

    @Operation(summary = "上传图片", description = "支持 jpg/png/gif/webp/svg，最大 10MB，仅作者/管理员可用")
    @PostMapping("/image")
    public ResponseEntity<ApiResponse<UploadImageVO>> uploadImage(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "请选择要上传的图片"));
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "文件名不能为空"));
        }

        String ext = originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "不支持的图片格式，仅支持 jpg/png/gif/webp/svg"));
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(ApiResponse.error(413, "图片大小超过限制（最大 10MB）"));
        }

        String contentType = file.getContentType();
        if (contentType == null) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        try {
            String url = fileStorage.store(
                    file.getInputStream(), originalName, file.getSize(), contentType);

            String fileName = url.substring(url.lastIndexOf('/') + 1);
            UploadImageVO data = new UploadImageVO(url, fileName, file.getSize(), contentType);
            return ResponseEntity.ok(ApiResponse.success(data));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(500, "上传失败：" + e.getMessage()));
        }
    }
}
