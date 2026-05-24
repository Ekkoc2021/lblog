package com.yang.lblogserver.image.controller;

import com.yang.lblogserver.common.ApiResponse;
import com.yang.lblogserver.image.domain.Images;
import com.yang.lblogserver.auth.security.model.LoginUser;
import com.yang.lblogserver.image.service.ImagesService;
import com.yang.lblogserver.storage.FileStorage;
import com.yang.lblogserver.storage.StorageResult;
import com.yang.lblogserver.image.vo.UploadImageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
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
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Tag(name = "上传", description = "图片上传")
@RestController
@RequestMapping("/api/v1/upload")
@PreAuthorize("hasRole('AUTHOR')")
public class UploadController {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp", "svg");
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    // 常见图片格式魔术字节
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47};
    private static final byte[] GIF_MAGIC = {0x47, 0x49, 0x46, 0x38};
    private static final byte[] WEBP_RIFF = {0x52, 0x49, 0x46, 0x46}; // RIFF....WEBP

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

    /** 校验文件魔术字节是否与扩展名匹配 */
    private boolean validateMagicBytes(byte[] bytes, String ext) {
        if (bytes.length < 4) return false;
        return switch (ext) {
            case "jpg", "jpeg" -> startsWith(bytes, JPEG_MAGIC);
            case "png" -> startsWith(bytes, PNG_MAGIC);
            case "gif" -> startsWith(bytes, GIF_MAGIC);
            case "webp" -> startsWith(bytes, WEBP_RIFF) && bytes.length > 12
                    && bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50; // "WEBP"
            case "svg" -> isSvgContent(bytes);
            default -> false;
        };
    }

    private boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) return false;
        }
        return true;
    }

    /** 检测文件内容是否为 SVG（文本格式，以 &lt;?xml、&lt;svg 或 &lt;!DOCTYPE svg 开头） */
    private boolean isSvgContent(byte[] bytes) {
        String head = new String(bytes, 0, Math.min(bytes.length, 256), StandardCharsets.UTF_8)
                .trim().toLowerCase();
        return head.startsWith("<?xml") || head.startsWith("<svg") || head.startsWith("<!doctype svg");
    }

    /** SVG 净化：删除 &lt;script&gt; 标签和 on* 事件属性 */
    private byte[] sanitizeSvg(byte[] bytes) {
        String svg = new String(bytes, StandardCharsets.UTF_8);
        Document doc = Jsoup.parse(svg, org.jsoup.parser.Parser.xmlParser());
        // 删除所有 script 元素
        doc.select("script").remove();
        // 删除所有元素上的 on* 事件属性
        doc.getAllElements().forEach(el -> {
            el.attributes().forEach(attr -> {
                if (attr.getKey().toLowerCase().startsWith("on")) {
                    el.removeAttr(attr.getKey());
                }
            });
        });
        return doc.outerHtml().getBytes(StandardCharsets.UTF_8);
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

        try {
            byte[] fileBytes = file.getBytes();

            // 魔术字节校验
            if (!validateMagicBytes(fileBytes, ext)) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(400, "文件内容与扩展名不匹配"));
            }

            // SVG 净化
            if ("svg".equals(ext)) {
                fileBytes = sanitizeSvg(fileBytes);
            }

            String md5 = DigestUtils.md5DigestAsHex(fileBytes);

            // 去重：检查是否已存在相同 MD5 的图片
            Images existing = imagesService.findByMd5(md5);
            if (existing != null) {
                UploadImageVO data = new UploadImageVO(existing.getUrl(), existing.getOriginalName(),
                        existing.getFileSize(), existing.getMimeType());
                data.setImageId(existing.getId());
                return ResponseEntity.ok(ApiResponse.success(data));
            }

            String contentType = file.getContentType();
            if (contentType == null) {
                contentType = "svg".equals(ext) ? "image/svg+xml" : MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }

            // 存储文件
            StorageResult result = fileStorage.store(new ByteArrayInputStream(fileBytes),
                    originalName, fileBytes.length, contentType);

            String fileName = result.getUrl().substring(result.getUrl().lastIndexOf('/') + 1);

            Long userId = getCurrentUserId();
            Long imageId = imagesService.recordImage(result.getUrl(), result.getStoragePath(),
                    originalName, contentType, fileBytes.length, null, null, md5, userId);

            UploadImageVO data = new UploadImageVO(result.getUrl(), fileName, fileBytes.length, contentType);
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
