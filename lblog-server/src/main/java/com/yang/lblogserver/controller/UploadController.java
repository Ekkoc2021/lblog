package com.yang.lblogserver.controller;

import com.yang.lblogserver.common.ApiResponse;
import com.yang.lblogserver.domain.Images;
import com.yang.lblogserver.security.model.LoginUser;
import com.yang.lblogserver.service.ImagesService;
import com.yang.lblogserver.storage.FileStorage;
import com.yang.lblogserver.storage.StorageResult;
import com.yang.lblogserver.vo.UploadImageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Set;

@Tag(name = "上传", description = "图片上传")
@RestController
@RequestMapping("/api/v1/upload")
@PreAuthorize("hasRole('AUTHOR')")
public class UploadController {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp", "svg");
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    private final FileStorage fileStorage;
    private final ImagesService imagesService;

    public UploadController(FileStorage fileStorage, ImagesService imagesService) {
        this.fileStorage = fileStorage;
        this.imagesService = imagesService;
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
            return ((LoginUser) authentication.getPrincipal()).getUserId();
        }
        return null;
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
            // 计算 MD5 用于去重
            byte[] fileBytes = file.getBytes();
            String md5 = DigestUtils.md5DigestAsHex(fileBytes);

            // 去重：检查是否已存在相同 MD5 的图片
            Images existing = imagesService.findByMd5(md5);
            if (existing != null) {
                // 去重命中，直接返回已有记录
                UploadImageVO data = new UploadImageVO(existing.getUrl(), existing.getOriginalName(),
                        existing.getFileSize(), existing.getMimeType());
                data.setImageId(existing.getId());
                return ResponseEntity.ok(ApiResponse.success(data));
            }

            // 存储文件
            StorageResult result = fileStorage.store(new ByteArrayInputStream(fileBytes),
                    originalName, file.getSize(), contentType);

            String fileName = result.getUrl().substring(result.getUrl().lastIndexOf('/') + 1);

            // 记录图片到 images 表（url=浏览器访问地址, storagePath=存储后端路径）
            Long userId = getCurrentUserId();
            Long imageId = imagesService.recordImage(result.getUrl(), result.getStoragePath(),
                    originalName, contentType, file.getSize(), null, null, md5, userId);

            UploadImageVO data = new UploadImageVO(result.getUrl(), fileName, file.getSize(), contentType);
            data.setImageId(imageId);
            return ResponseEntity.ok(ApiResponse.success(data));
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(500, "上传失败：" + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(500, "上传失败：" + e.getMessage()));
        }
    }
}
