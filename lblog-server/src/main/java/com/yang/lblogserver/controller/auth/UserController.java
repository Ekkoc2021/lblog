package com.yang.lblogserver.controller.auth;

import com.yang.lblogserver.common.ApiResponse;
import com.yang.lblogserver.domain.ImageUsage;
import com.yang.lblogserver.domain.Images;
import com.yang.lblogserver.mapper.ImageUsageMapper;
import com.yang.lblogserver.mapper.UsersMapper;
import com.yang.lblogserver.security.model.LoginUser;
import com.yang.lblogserver.service.ImagesService;
import com.yang.lblogserver.storage.FileStorage;
import com.yang.lblogserver.storage.StorageResult;
import com.yang.lblogserver.vo.response.AvatarVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Set;

@Tag(name = "用户", description = "用户头像管理")
@Validated
@RestController
@RequestMapping("/api/v1/user")
@PreAuthorize("isAuthenticated()")
public class UserController {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp", "svg");
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    private final FileStorage fileStorage;
    private final ImagesService imagesService;
    private final ImageUsageMapper imageUsageMapper;
    private final UsersMapper usersMapper;

    public UserController(FileStorage fileStorage, ImagesService imagesService,
                          ImageUsageMapper imageUsageMapper, UsersMapper usersMapper) {
        this.fileStorage = fileStorage;
        this.imagesService = imagesService;
        this.imageUsageMapper = imageUsageMapper;
        this.usersMapper = usersMapper;
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
            return ((LoginUser) authentication.getPrincipal()).getUserId();
        }
        return null;
    }

    @Operation(summary = "上传头像", description = "上传或更换用户头像，仅支持图片格式，最大 10MB")
    @PutMapping("/avatar")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<ApiResponse<AvatarVO>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, "未登录"));
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "请选择要上传的图片"));
        }

        // 校验文件类型
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isEmpty()) {
            originalName = "avatar.jpg";
        }

        String ext = originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "不支持的图片格式，仅支持 jpg/png/gif/webp/svg"));
        }

        // 校验文件大小
        if (file.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(ApiResponse.error(413, "图片大小超过限制（最大 10MB）"));
        }

        try {
            // 计算 MD5 用于去重
            byte[] fileBytes = file.getBytes();
            String md5 = DigestUtils.md5DigestAsHex(fileBytes);

            // 去重：检查是否已存在相同 MD5 的图片
            Images existing = imagesService.findByMd5(md5);
            Long imageId;
            String url;

            if (existing != null) {
                // 去重命中，直接使用已有记录
                imageId = existing.getId();
                url = existing.getUrl();
            } else {
                // 存储文件
                String ct = file.getContentType();
                if (ct == null) ct = MediaType.APPLICATION_OCTET_STREAM_VALUE;

                StorageResult result = fileStorage.store(new ByteArrayInputStream(fileBytes),
                        originalName, file.getSize(), ct);

                url = result.getUrl();

                // 记录图片到 images 表
                imageId = imagesService.recordImage(result.getUrl(), result.getStoragePath(),
                        originalName, ct, file.getSize(), null, null, md5, userId);
            }

            // 事务内：清理旧引用 + 插入新引用 + 更新用户头像
            imageUsageMapper.deleteByRefAndField("user", userId, "avatar");

            ImageUsage usage = new ImageUsage();
            usage.setImageId(imageId);
            usage.setRefType("user");
            usage.setRefId(userId);
            usage.setField("avatar");
            imageUsageMapper.insert(usage);

            usersMapper.updateAvatar(userId, url);

            return ResponseEntity.ok(ApiResponse.success(new AvatarVO(imageId, url)));
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(500, "上传失败：" + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(500, "上传失败：" + e.getMessage()));
        }
    }

    @Operation(summary = "删除头像", description = "删除用户头像，恢复默认显示")
    @DeleteMapping("/avatar")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<?> deleteAvatar() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }

        // 删除引用
        imageUsageMapper.deleteByRefAndField("user", userId, "avatar");

        // 置空用户头像
        usersMapper.updateAvatar(userId, null);

        return ApiResponse.success(null);
    }
}
