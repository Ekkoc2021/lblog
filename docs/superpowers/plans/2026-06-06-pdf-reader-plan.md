# PDF 阅读器实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在工具箱中新增在线 PDF 阅读器，支持批注（高亮/画笔/下划线/便签）、书签、文件夹管理、大文件 Range 懒加载、阅读进度记录

**Architecture:** PDF.js 渲染 + Konva Canvas 批注层 + DokFlow 标注引擎集成。后端 FileStorage 抽象层处理 Range 请求，MySQL JSON 列按页存储标注。全屏页面 `/reader` 三栏布局。

**Tech Stack:** PDF.js (pdfjs-dist) + Konva/react-konva + Spring Boot Range 请求 + MySQL JSON

---

## 文件结构

| 操作 | 文件 | 职责 |
|------|------|------|
| 新建 | `auth/domain/PdfFile.java` | PDF 文件实体 |
| 新建 | `auth/domain/PdfFolder.java` | 文件夹实体 |
| 新建 | `auth/domain/PdfAnnotation.java` | 标注实体 |
| 新建 | `auth/domain/PdfBookmark.java` | 书签实体 |
| 新建 | `auth/domain/PdfProgress.java` | 进度实体 |
| 新建 | `auth/vo/PdfFileVO.java` | 文件列表响应 |
| 新建 | `auth/vo/PdfFolderVO.java` | 文件夹树响应 |
| 新建 | `auth/vo/PdfAnnotationRequest.java` | 标注保存请求 |
| 新建 | `auth/vo/PdfBookmarkRequest.java` | 书签请求 |
| 新建 | `auth/vo/PdfProgressRequest.java` | 进度请求 |
| 新建 | `auth/mapper/PdfFileMapper.java + XML` | 文件 Mapper |
| 新建 | `auth/mapper/PdfFolderMapper.java + XML` | 文件夹 Mapper |
| 新建 | `auth/mapper/PdfAnnotationMapper.java + XML` | 标注 Mapper |
| 新建 | `auth/mapper/PdfBookmarkMapper.java + XML` | 书签 Mapper |
| 新建 | `auth/mapper/PdfProgressMapper.java + XML` | 进度 Mapper |
| 新建 | `auth/service/PdfService.java` | 核心业务逻辑 |
| 新建 | `auth/controller/PdfController.java` | 文件+文件夹接口 |
| 新建 | `auth/controller/PdfAnnotationController.java` | 标注接口 |
| 新建 | `auth/controller/PdfBookmarkController.java` | 书签接口 |
| 新建 | `auth/controller/PdfProgressController.java` | 进度接口 |
| 修改 | `application.yml` | multipart max-file-size=500MB |
| 新建 | `resources/sql/pdf_reader_v1.sql` | 5 张表 DDL |
| 新建 | `lblog-web/src/pages/PdfReaderPage.tsx` | 全屏页面 |
| 新建 | `lblog-web/src/components/pdf/PdfSidebar.tsx` | 左侧面板容器 |
| 新建 | `lblog-web/src/components/pdf/FolderTree.tsx` | 文件夹树 |
| 新建 | `lblog-web/src/components/pdf/PdfFileList.tsx` | 文件列表 |
| 新建 | `lblog-web/src/components/pdf/BookmarkPanel.tsx` | 书签面板 |
| 新建 | `lblog-web/src/components/pdf/PdfViewer.tsx` | 阅读主区 |
| 新建 | `lblog-web/src/components/pdf/PdfToolbar.tsx` | 批注工具条 |
| 新建 | `lblog-web/src/components/pdf/PdfUploadModal.tsx` | 上传弹窗 |
| 修改 | `lblog-web/src/types/index.ts` | 新增 5 个类型 |
| 修改 | `lblog-web/src/services/api.ts` | 新增 15 个 API 函数 |
| 修改 | `lblog-web/src/App.tsx` | `/reader` 路由 |
| 修改 | `lblog-web/src/components/DrawFloatingButton.tsx` | PDF 阅读入口 |

---

### Task 1: 数据库 DDL + 后端 Domain 实体

**Files:**
- Create: `lblog-server/src/main/resources/sql/pdf_reader_v1.sql`
- Create: `lblog-server/src/main/java/com/yang/lblogserver/auth/domain/PdfFile.java`
- Create: `lblog-server/src/main/java/com/yang/lblogserver/auth/domain/PdfFolder.java`
- Create: `lblog-server/src/main/java/com/yang/lblogserver/auth/domain/PdfAnnotation.java`
- Create: `lblog-server/src/main/java/com/yang/lblogserver/auth/domain/PdfBookmark.java`
- Create: `lblog-server/src/main/java/com/yang/lblogserver/auth/domain/PdfProgress.java`

- [ ] **Step 1: 创建 SQL DDL**

```sql
-- pdf_reader_v1.sql
CREATE TABLE IF NOT EXISTS pdf_folders (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    parent_id       BIGINT       NULL,
    name            VARCHAR(100) NOT NULL,
    sort_order      INT          DEFAULT 0,
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_parent (parent_id),
    CONSTRAINT fk_folder_parent FOREIGN KEY (parent_id) REFERENCES pdf_folders(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS pdf_files (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    folder_id       BIGINT       NULL,
    filename        VARCHAR(255) NOT NULL,
    original_name   VARCHAR(255) NOT NULL,
    file_size       BIGINT       NOT NULL,
    file_path       VARCHAR(500) NOT NULL,
    total_pages     INT          DEFAULT 0,
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_folder (user_id, folder_id),
    INDEX idx_user (user_id),
    CONSTRAINT fk_file_folder FOREIGN KEY (folder_id) REFERENCES pdf_folders(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS pdf_annotations (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    pdf_id          BIGINT       NOT NULL,
    page_num        INT          NOT NULL,
    user_id         BIGINT       NOT NULL,
    data            JSON         NOT NULL,
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_pdf_page_user (pdf_id, page_num, user_id),
    CONSTRAINT fk_ann_file FOREIGN KEY (pdf_id) REFERENCES pdf_files(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS pdf_bookmarks (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    pdf_id          BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    page_num        INT          NOT NULL,
    label           VARCHAR(100) NOT NULL,
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_pdf_user (pdf_id, user_id),
    CONSTRAINT fk_bm_file FOREIGN KEY (pdf_id) REFERENCES pdf_files(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS pdf_progress (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    pdf_id          BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    page_num        INT          DEFAULT 1,
    scroll_top      FLOAT        DEFAULT 0,
    updated_at      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_pdf_user (pdf_id, user_id),
    CONSTRAINT fk_prog_file FOREIGN KEY (pdf_id) REFERENCES pdf_files(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **Step 2: 创建 5 个 Domain 实体**

```java
// PdfFile.java
package com.yang.lblogserver.auth.domain;
import lombok.Data;
import java.util.Date;

@Data
public class PdfFile {
    private Long id;
    private Long userId;
    private Long folderId;
    private String filename;
    private String originalName;
    private Long fileSize;
    private String filePath;
    private Integer totalPages;
    private Date createdAt;
    private Date updatedAt;
}
```

```java
// PdfFolder.java
package com.yang.lblogserver.auth.domain;
import lombok.Data;
import java.util.Date;
import java.util.List;

@Data
public class PdfFolder {
    private Long id;
    private Long userId;
    private Long parentId;
    private String name;
    private Integer sortOrder;
    private Date createdAt;
    private Date updatedAt;
    private List<PdfFolder> children;  // 前端树结构
}
```

```java
// PdfAnnotation.java
package com.yang.lblogserver.auth.domain;
import lombok.Data;
import java.util.Date;

@Data
public class PdfAnnotation {
    private Long id;
    private Long pdfId;
    private Integer pageNum;
    private Long userId;
    private String data;   // JSON string
    private Date createdAt;
    private Date updatedAt;
}
```

```java
// PdfBookmark.java
package com.yang.lblogserver.auth.domain;
import lombok.Data;
import java.util.Date;

@Data
public class PdfBookmark {
    private Long id;
    private Long pdfId;
    private Long userId;
    private Integer pageNum;
    private String label;
    private Date createdAt;
}
```

```java
// PdfProgress.java
package com.yang.lblogserver.auth.domain;
import lombok.Data;
import java.util.Date;

@Data
public class PdfProgress {
    private Long id;
    private Long pdfId;
    private Long userId;
    private Integer pageNum;
    private Float scrollTop;
    private Date updatedAt;
}
```

- [ ] **Step 3: Commit**

```bash
git add lblog-server/src/main/resources/sql/pdf_reader_v1.sql \
        lblog-server/src/main/java/com/yang/lblogserver/auth/domain/PdfFile.java \
        lblog-server/src/main/java/com/yang/lblogserver/auth/domain/PdfFolder.java \
        lblog-server/src/main/java/com/yang/lblogserver/auth/domain/PdfAnnotation.java \
        lblog-server/src/main/java/com/yang/lblogserver/auth/domain/PdfBookmark.java \
        lblog-server/src/main/java/com/yang/lblogserver/auth/domain/PdfProgress.java
git commit -m "feat: PDF 阅读器 — DDL + Domain 实体"
```

---

### Task 2: 后端 VO 类

**Files:**
- Create: `lblog-server/src/main/java/com/yang/lblogserver/auth/vo/PdfFileVO.java`
- Create: `lblog-server/src/main/java/com/yang/lblogserver/auth/vo/PdfFolderVO.java`
- Create: `lblog-server/src/main/java/com/yang/lblogserver/auth/vo/PdfAnnotationRequest.java`
- Create: `lblog-server/src/main/java/com/yang/lblogserver/auth/vo/PdfBookmarkRequest.java`
- Create: `lblog-server/src/main/java/com/yang/lblogserver/auth/vo/PdfProgressRequest.java`

- [ ] **Step 1: 创建所有 VO**

```java
// PdfFileVO.java
package com.yang.lblogserver.auth.vo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;

@Schema(description = "PDF 文件视图")
public class PdfFileVO {
    @Schema(description = "文件ID") private Long id;
    @Schema(description = "文件夹ID") private Long folderId;
    @Schema(description = "原始文件名") private String originalName;
    @Schema(description = "文件大小(字节)") private Long fileSize;
    @Schema(description = "总页数") private Integer totalPages;
    @Schema(description = "上传时间") private Date createdAt;
    @Schema(description = "更新时间") private Date updatedAt;
    
    // getters/setters omitted for brevity — follow existing project VO pattern
}
```

```java
// PdfFolderVO.java
package com.yang.lblogserver.auth.vo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;
import java.util.List;

@Schema(description = "PDF 文件夹视图")
public class PdfFolderVO {
    @Schema(description = "文件夹ID") private Long id;
    @Schema(description = "父文件夹ID") private Long parentId;
    @Schema(description = "文件夹名") private String name;
    @Schema(description = "排序") private Integer sortOrder;
    @Schema(description = "子文件夹") private List<PdfFolderVO> children;
    @Schema(description = "创建时间") private Date createdAt;
    
    // getters/setters
}
```

```java
// PdfAnnotationRequest.java
package com.yang.lblogserver.auth.vo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "标注保存请求")
public class PdfAnnotationRequest {
    @NotNull @Schema(description = "标注 JSON 数组") private String data;
    public String getData() { return data; }
    public void setData(String data) { this.data = data; }
}
```

```java
// PdfBookmarkRequest.java
package com.yang.lblogserver.auth.vo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "书签请求")
public class PdfBookmarkRequest {
    @NotNull @Schema(description = "页码") private Integer pageNum;
    @NotBlank @Schema(description = "书签名") private String label;
    // getters/setters
}
```

```java
// PdfProgressRequest.java
package com.yang.lblogserver.auth.vo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "进度请求")
public class PdfProgressRequest {
    @NotNull @Schema(description = "页码") private Integer pageNum;
    @Schema(description = "滚动偏移") private Float scrollTop;
    // getters/setters
}
```

- [ ] **Step 2: Commit**

```bash
git add lblog-server/src/main/java/com/yang/lblogserver/auth/vo/PdfFileVO.java \
        lblog-server/src/main/java/com/yang/lblogserver/auth/vo/PdfFolderVO.java \
        lblog-server/src/main/java/com/yang/lblogserver/auth/vo/PdfAnnotationRequest.java \
        lblog-server/src/main/java/com/yang/lblogserver/auth/vo/PdfBookmarkRequest.java \
        lblog-server/src/main/java/com/yang/lblogserver/auth/vo/PdfProgressRequest.java
git commit -m "feat: PDF 阅读器 — VO 类"
```

---

### Task 3: 后端 Mapper 接口 + XML

**Files:**
- Create: 5 个 Mapper 接口 + 5 个 XML

- [ ] **Step 1: PdfFileMapper**

```java
// PdfFileMapper.java
package com.yang.lblogserver.auth.mapper;
import com.yang.lblogserver.auth.domain.PdfFile;
import com.yang.lblogserver.auth.vo.PdfFileVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface PdfFileMapper {
    int insert(PdfFile file);
    PdfFile selectById(@Param("id") Long id);
    List<PdfFileVO> selectByUserAndFolder(@Param("userId") Long userId, @Param("folderId") Long folderId);
    List<PdfFileVO> selectRootFiles(@Param("userId") Long userId);
    int update(@Param("id") Long id, @Param("originalName") String originalName, @Param("folderId") Long folderId);
    int delete(@Param("id") Long id);
    int updateTotalPages(@Param("id") Long id, @Param("totalPages") Integer totalPages);
}
```

```xml
<!-- PdfFileMapper.xml -->
<mapper namespace="com.yang.lblogserver.auth.mapper.PdfFileMapper">
    <resultMap id="BaseResultMap" type="com.yang.lblogserver.auth.domain.PdfFile">
        <id property="id" column="id"/>
        <result property="userId" column="user_id"/>
        <result property="folderId" column="folder_id"/>
        <result property="filename" column="filename"/>
        <result property="originalName" column="original_name"/>
        <result property="fileSize" column="file_size"/>
        <result property="filePath" column="file_path"/>
        <result property="totalPages" column="total_pages"/>
        <result property="createdAt" column="created_at"/>
        <result property="updatedAt" column="updated_at"/>
    </resultMap>

    <resultMap id="FileVOMap" type="com.yang.lblogserver.auth.vo.PdfFileVO">
        <id property="id" column="id"/>
        <result property="folderId" column="folder_id"/>
        <result property="originalName" column="original_name"/>
        <result property="fileSize" column="file_size"/>
        <result property="totalPages" column="total_pages"/>
        <result property="createdAt" column="created_at"/>
        <result property="updatedAt" column="updated_at"/>
    </resultMap>

    <insert id="insert" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO pdf_files (user_id, folder_id, filename, original_name, file_size, file_path, total_pages, created_at, updated_at)
        VALUES (#{userId}, #{folderId}, #{filename}, #{originalName}, #{fileSize}, #{filePath}, #{totalPages}, NOW(), NOW())
    </insert>

    <select id="selectById" resultMap="BaseResultMap">
        SELECT id, user_id, folder_id, filename, original_name, file_size, file_path, total_pages, created_at, updated_at
        FROM pdf_files WHERE id = #{id}
    </select>

    <select id="selectByUserAndFolder" resultMap="FileVOMap">
        SELECT id, folder_id, original_name, file_size, total_pages, created_at, updated_at
        FROM pdf_files WHERE user_id = #{userId}
        <if test="folderId != null">AND folder_id = #{folderId}</if>
        <if test="folderId == null">AND folder_id IS NULL</if>
        ORDER BY updated_at DESC
    </select>

    <select id="selectRootFiles" resultMap="FileVOMap">
        SELECT id, folder_id, original_name, file_size, total_pages, created_at, updated_at
        FROM pdf_files WHERE user_id = #{userId} AND folder_id IS NULL
        ORDER BY updated_at DESC
    </select>

    <update id="update">
        UPDATE pdf_files SET original_name = #{originalName}, folder_id = #{folderId}, updated_at = NOW()
        WHERE id = #{id}
    </update>

    <delete id="delete">
        DELETE FROM pdf_files WHERE id = #{id}
    </delete>

    <update id="updateTotalPages">
        UPDATE pdf_files SET total_pages = #{totalPages}, updated_at = NOW() WHERE id = #{id}
    </update>
</mapper>
```

- [ ] **Step 2: PdfFolderMapper**

```java
// PdfFolderMapper.java
@Mapper
public interface PdfFolderMapper {
    int insert(PdfFolder folder);
    PdfFolder selectById(@Param("id") Long id);
    List<PdfFolder> selectByUser(@Param("userId") Long userId);
    List<PdfFolder> selectByUserAndParent(@Param("userId") Long userId, @Param("parentId") Long parentId);
    int update(@Param("id") Long id, @Param("name") String name, @Param("parentId") Long parentId);
    int delete(@Param("id") Long id);
    List<PdfFolder> selectChildren(@Param("parentId") Long parentId);
}
```

XML — 参照 PdfFileMapper 模式，包含 BaseResultMap、insert/select/update/delete 语句。

- [ ] **Step 3: PdfAnnotationMapper**

```java
@Mapper
public interface PdfAnnotationMapper {
    int upsert(PdfAnnotation ann);
    PdfAnnotation selectByPdfPageUser(@Param("pdfId") Long pdfId, @Param("pageNum") Integer pageNum, @Param("userId") Long userId);
    int deleteByPdfId(@Param("pdfId") Long pdfId);
}
```

XML — upsert 使用 `INSERT ... ON DUPLICATE KEY UPDATE`：

```xml
<insert id="upsert">
    INSERT INTO pdf_annotations (pdf_id, page_num, user_id, data, created_at, updated_at)
    VALUES (#{pdfId}, #{pageNum}, #{userId}, #{data}, NOW(), NOW())
    ON DUPLICATE KEY UPDATE data = VALUES(data), updated_at = NOW()
</insert>
```

- [ ] **Step 4: PdfBookmarkMapper**

```java
@Mapper
public interface PdfBookmarkMapper {
    int insert(PdfBookmark bm);
    List<PdfBookmark> selectByPdfUser(@Param("pdfId") Long pdfId, @Param("userId") Long userId);
    int delete(@Param("id") Long id);
    int deleteByPdfId(@Param("pdfId") Long pdfId);
}
```

- [ ] **Step 5: PdfProgressMapper**

```java
@Mapper
public interface PdfProgressMapper {
    int upsert(PdfProgress prog);
    PdfProgress selectByPdfUser(@Param("pdfId") Long pdfId, @Param("userId") Long userId);
}
```

- [ ] **Step 6: Commit**

```bash
git add lblog-server/src/main/java/com/yang/lblogserver/auth/mapper/PdfFileMapper.java \
        lblog-server/src/main/java/com/yang/lblogserver/auth/mapper/PdfFolderMapper.java \
        lblog-server/src/main/java/com/yang/lblogserver/auth/mapper/PdfAnnotationMapper.java \
        lblog-server/src/main/java/com/yang/lblogserver/auth/mapper/PdfBookmarkMapper.java \
        lblog-server/src/main/java/com/yang/lblogserver/auth/mapper/PdfProgressMapper.java \
        lblog-server/src/main/resources/com/yang/lblogserver/auth/mapper/
git commit -m "feat: PDF 阅读器 — Mapper 接口 + XML"
```

---

### Task 4: 后端 PdfService

**Files:**
- Create: `lblog-server/src/main/java/com/yang/lblogserver/auth/service/PdfService.java`

```java
package com.yang.lblogserver.auth.service;

import com.yang.lblogserver.auth.domain.*;
import com.yang.lblogserver.auth.mapper.*;
import com.yang.lblogserver.auth.vo.*;
import com.yang.lblogserver.storage.FileStorage;
import com.yang.lblogserver.storage.StorageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PdfService {

    private final PdfFileMapper fileMapper;
    private final PdfFolderMapper folderMapper;
    private final PdfAnnotationMapper annotationMapper;
    private final PdfBookmarkMapper bookmarkMapper;
    private final PdfProgressMapper progressMapper;
    private final FileStorage fileStorage;

    public PdfService(PdfFileMapper fileMapper, PdfFolderMapper folderMapper,
                      PdfAnnotationMapper annotationMapper, PdfBookmarkMapper bookmarkMapper,
                      PdfProgressMapper progressMapper, FileStorage fileStorage) {
        this.fileMapper = fileMapper;
        this.folderMapper = folderMapper;
        this.annotationMapper = annotationMapper;
        this.bookmarkMapper = bookmarkMapper;
        this.progressMapper = progressMapper;
        this.fileStorage = fileStorage;
    }

    /** 上传 PDF */
    @Transactional
    public PdfFile upload(Long userId, MultipartFile file, Long folderId) throws IOException {
        // 校验 MIME type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals("application/pdf")) {
            throw new IllegalArgumentException("仅支持 PDF 格式");
        }
        if (file.isEmpty()) throw new IllegalArgumentException("文件为空");

        String originalName = file.getOriginalFilename();
        String ext = ".pdf";
        String storedName = UUID.randomUUID().toString() + ext;

        // 存储文件
        StorageResult result = fileStorage.store(file.getInputStream(), storedName, file.getSize(), contentType);

        PdfFile pdfFile = new PdfFile();
        pdfFile.setUserId(userId);
        pdfFile.setFolderId(folderId);
        pdfFile.setFilename(storedName);
        pdfFile.setOriginalName(originalName);
        pdfFile.setFileSize(file.getSize());
        pdfFile.setFilePath(result.getStoragePath());
        pdfFile.setTotalPages(0);
        fileMapper.insert(pdfFile);

        return pdfFile;
    }

    /** 更新 PDF 总页数 */
    public void updateTotalPages(Long fileId, int totalPages) {
        fileMapper.updateTotalPages(fileId, totalPages);
    }

    /** 我的 PDF 列表 */
    public List<PdfFileVO> getFiles(Long userId, Long folderId) {
        return fileMapper.selectByUserAndFolder(userId, folderId);
    }

    /** PDF 详情 */
    public PdfFile getFile(Long id) {
        PdfFile f = fileMapper.selectById(id);
        if (f == null) throw new IllegalArgumentException("文件不存在");
        return f;
    }

    /** 重命名/移动 */
    public void updateFile(Long id, String originalName, Long folderId) {
        fileMapper.update(id, originalName, folderId);
    }

    /** 删除 PDF */
    @Transactional
    public void deleteFile(Long id) {
        PdfFile f = fileMapper.selectById(id);
        if (f != null) {
            // 物理删除文件
            try { fileStorage.delete(f.getFilePath()); } catch (Exception ignored) {}
            annotationMapper.deleteByPdfId(id);
            bookmarkMapper.deleteByPdfId(id);
            fileMapper.delete(id);
        }
    }

    /** 文件夹 CRUD */
    public List<PdfFolderVO> getFolderTree(Long userId) {
        List<PdfFolder> all = folderMapper.selectByUser(userId);
        Map<Long, PdfFolderVO> map = new HashMap<>();
        List<PdfFolderVO> roots = new ArrayList<>();

        for (PdfFolder f : all) {
            PdfFolderVO vo = new PdfFolderVO();
            vo.setId(f.getId()); vo.setParentId(f.getParentId());
            vo.setName(f.getName()); vo.setSortOrder(f.getSortOrder());
            vo.setCreatedAt(f.getCreatedAt()); vo.setChildren(new ArrayList<>());
            map.put(f.getId(), vo);
        }
        for (PdfFolderVO vo : map.values()) {
            if (vo.getParentId() == null || !map.containsKey(vo.getParentId())) {
                roots.add(vo);
            } else {
                map.get(vo.getParentId()).getChildren().add(vo);
            }
        }
        return roots;
    }

    public PdfFolder createFolder(Long userId, String name, Long parentId) {
        // 防循环：如果 parentId 非空，验证属于同一用户
        if (parentId != null) {
            PdfFolder parent = folderMapper.selectById(parentId);
            if (parent == null || !parent.getUserId().equals(userId))
                throw new IllegalArgumentException("父文件夹不存在");
        }
        PdfFolder f = new PdfFolder();
        f.setUserId(userId); f.setName(name); f.setParentId(parentId);
        folderMapper.insert(f);
        return f;
    }

    public void updateFolder(Long id, String name, Long parentId) {
        // 防循环
        if (parentId != null && parentId.equals(id))
            throw new IllegalArgumentException("不能移动到自身");
        folderMapper.update(id, name, parentId);
    }

    @Transactional
    public void deleteFolder(Long id) {
        // 子文件夹级联删除（DB 外键 ON DELETE CASCADE）
        // 文件夹内文件 folder_id 设为 null
        folderMapper.delete(id);
    }

    /** 标注读写 */
    public String getAnnotation(Long pdfId, int pageNum, Long userId) {
        PdfAnnotation ann = annotationMapper.selectByPdfPageUser(pdfId, pageNum, userId);
        return ann != null ? ann.getData() : "[]";
    }

    public void saveAnnotation(Long pdfId, int pageNum, Long userId, String data) {
        PdfAnnotation ann = new PdfAnnotation();
        ann.setPdfId(pdfId); ann.setPageNum(pageNum);
        ann.setUserId(userId); ann.setData(data);
        annotationMapper.upsert(ann);
    }

    /** 书签 CRUD */
    public List<PdfBookmark> getBookmarks(Long pdfId, Long userId) {
        return bookmarkMapper.selectByPdfUser(pdfId, userId);
    }

    public PdfBookmark addBookmark(Long pdfId, Long userId, int pageNum, String label) {
        PdfBookmark bm = new PdfBookmark();
        bm.setPdfId(pdfId); bm.setUserId(userId);
        bm.setPageNum(pageNum); bm.setLabel(label);
        bookmarkMapper.insert(bm);
        return bm;
    }

    public void deleteBookmark(Long id) {
        bookmarkMapper.delete(id);
    }

    /** 进度 */
    public PdfProgress getProgress(Long pdfId, Long userId) {
        PdfProgress p = progressMapper.selectByPdfUser(pdfId, userId);
        if (p == null) { p = new PdfProgress(); p.setPageNum(1); p.setScrollTop(0f); }
        return p;
    }

    public void saveProgress(Long pdfId, Long userId, int pageNum, Float scrollTop) {
        PdfProgress p = new PdfProgress();
        p.setPdfId(pdfId); p.setUserId(userId);
        p.setPageNum(pageNum); p.setScrollTop(scrollTop != null ? scrollTop : 0f);
        progressMapper.upsert(p);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add lblog-server/src/main/java/com/yang/lblogserver/auth/service/PdfService.java
git commit -m "feat: PDF 阅读器 — PdfService"
```

---

### Task 5: Controller — PdfController（文件+文件夹）

```java
package com.yang.lblogserver.auth.controller;

import com.yang.lblogserver.auth.domain.PdfFile;
import com.yang.lblogserver.auth.security.model.LoginUser;
import com.yang.lblogserver.auth.service.PdfService;
import com.yang.lblogserver.auth.vo.PdfFileVO;
import com.yang.lblogserver.auth.vo.PdfFolderVO;
import com.yang.lblogserver.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "PDF 阅读器", description = "文件管理与文件夹")
@RestController
@RequestMapping("/api/v1/pdf")
@PreAuthorize("isAuthenticated()")
public class PdfController {

    private final PdfService pdfService;

    public PdfController(PdfService pdfService) { this.pdfService = pdfService; }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser)
            return ((LoginUser) auth.getPrincipal()).getUserId();
        return null;
    }

    @Operation(summary = "上传 PDF")
    @PostMapping("/upload")
    public ApiResponse<PdfFile> upload(@RequestParam("file") MultipartFile file,
                                        @RequestParam(required = false) Long folderId) {
        Long userId = getCurrentUserId();
        try {
            PdfFile pdfFile = pdfService.upload(userId, file, folderId);
            return ApiResponse.success(pdfFile);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "上传失败: " + e.getMessage());
        }
    }

    @Operation(summary = "PDF 文件流（Range 请求）")
    @GetMapping("/files/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        PdfFile f = pdfService.getFile(id);
        Long userId = getCurrentUserId();
        if (!f.getUserId().equals(userId)) return ResponseEntity.status(403).build();

        Resource resource = new FileSystemResource(f.getFilePath());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .body(resource);
    }

    @Operation(summary = "文件列表")
    @GetMapping("/files")
    public ApiResponse<List<PdfFileVO>> list(@RequestParam(required = false) Long folderId) {
        return ApiResponse.success(pdfService.getFiles(getCurrentUserId(), folderId));
    }

    @Operation(summary = "文件详情")
    @GetMapping("/files/{id}")
    public ApiResponse<PdfFile> detail(@PathVariable Long id) {
        return ApiResponse.success(pdfService.getFile(id));
    }

    @Operation(summary = "更新文件（重命名/移动）")
    @PutMapping("/files/{id}")
    public ApiResponse<?> update(@PathVariable Long id, @RequestParam(required = false) String originalName,
                                  @RequestParam(required = false) Long folderId) {
        pdfService.updateFile(id, originalName, folderId);
        return ApiResponse.success(null);
    }

    @Operation(summary = "删除文件")
    @DeleteMapping("/files/{id}")
    public ApiResponse<?> delete(@PathVariable Long id) {
        pdfService.deleteFile(id);
        return ApiResponse.success(null);
    }

    @Operation(summary = "更新 PDF 总页数")
    @PutMapping("/files/{id}/total-pages")
    public ApiResponse<?> updateTotalPages(@PathVariable Long id, @RequestParam int totalPages) {
        pdfService.updateTotalPages(id, totalPages);
        return ApiResponse.success(null);
    }

    // ---- 文件夹 ----

    @Operation(summary = "文件夹树")
    @GetMapping("/folders")
    public ApiResponse<List<PdfFolderVO>> folders() {
        return ApiResponse.success(pdfService.getFolderTree(getCurrentUserId()));
    }

    @Operation(summary = "创建文件夹")
    @PostMapping("/folders")
    public ApiResponse<PdfFolder> createFolder(@RequestParam String name,
                                                @RequestParam(required = false) Long parentId) {
        return ApiResponse.success(pdfService.createFolder(getCurrentUserId(), name, parentId));
    }

    @Operation(summary = "更新文件夹")
    @PutMapping("/folders/{id}")
    public ApiResponse<?> updateFolder(@PathVariable Long id, @RequestParam String name,
                                        @RequestParam(required = false) Long parentId) {
        pdfService.updateFolder(id, name, parentId);
        return ApiResponse.success(null);
    }

    @Operation(summary = "删除文件夹")
    @DeleteMapping("/folders/{id}")
    public ApiResponse<?> deleteFolder(@PathVariable Long id) {
        pdfService.deleteFolder(id);
        return ApiResponse.success(null);
    }
}
```

- [ ] **Commit**

```bash
git add lblog-server/src/main/java/com/yang/lblogserver/auth/controller/PdfController.java
git commit -m "feat: PDF 阅读器 — PdfController 文件+文件夹 CRUD"
```

---

### Task 6: Controller — PdfAnnotationController + PdfBookmarkController + PdfProgressController

```java
// PdfAnnotationController.java — GET/PUT annotations by page
@Tag(name = "PDF 阅读器", description = "标注管理")
@RestController
@RequestMapping("/api/v1/pdf")
@PreAuthorize("isAuthenticated()")
public class PdfAnnotationController {
    // GET /{pdfId}/annotations?page=50
    // PUT /{pdfId}/annotations/page/{pageNum}
}
```

```java
// PdfBookmarkController.java — CRUD bookmarks
@Tag(name = "PDF 阅读器", description = "书签管理")
@RestController
@RequestMapping("/api/v1/pdf")
@PreAuthorize("isAuthenticated()")
public class PdfBookmarkController {
    // GET /{pdfId}/bookmarks
    // POST /{pdfId}/bookmarks
    // DELETE /{pdfId}/bookmarks/{id}
}
```

```java
// PdfProgressController.java — GET/PUT progress
@Tag(name = "PDF 阅读器", description = "阅读进度")
@RestController
@RequestMapping("/api/v1/pdf")
@PreAuthorize("isAuthenticated()")
public class PdfProgressController {
    // GET /{pdfId}/progress
    // PUT /{pdfId}/progress
}
```

（完整代码按设计文档 API 定义实现，省略重复模式）

---

### Task 7: 配置 + SQL 执行

**Files:**
- Modify: `lblog-server/src/main/resources/application.yml` — 增加 `spring.servlet.multipart.max-file-size: 500MB`

- [ ] **Step 1: 修改配置**

在 application.yml 的 spring 段添加：
```yaml
spring:
  servlet:
    multipart:
      max-file-size: 500MB
      max-request-size: 500MB
```

- [ ] **Step 2: 执行 DDL**

通过 MySQL MCP 执行 `pdf_reader_v1.sql` 中的 5 条 CREATE TABLE 语句。

- [ ] **Step 3: Commit**

---

### Task 8: 前端 Types + API 函数

**Files:**
- Modify: `lblog-web/src/types/index.ts`
- Modify: `lblog-web/src/services/api.ts`

Add 5 TypeScript interfaces and 15 API functions matching the backend APIs.

---

### Task 9: 前端 PdfUploadModal + PdfReaderPage 骨架

Create upload modal and wire up `/reader` route with skeleton layout (top bar + sidebar placeholder + content area).

---

### Task 10: 前端 PdfSidebar + FolderTree + PdfFileList

Left panel with folder tree (recursive component), file list, context menus, drag-to-move.

---

### Task 11: 前端 BookmarkPanel

Bookmark list for current PDF: display sorted by page, add/delete, click-to-navigate.

---

### Task 12: 前端 PdfToolbar

Annotation toolbar: tool selection, color picker, stroke width, undo button.

---

### Task 13: 前端 PdfViewer（核心）

PDF.js initialization, page rendering with Range requests, Konva overlay for annotations, page navigation, progress auto-save, annotation load/save by page.

---

### Task 14: 工具箱入口 + DrawFloatingButton

Add "PDF 阅读" entry to the floating toolbox button, wire to `/reader` route.

---

### Task 15: 集成测试

Backend: `mvn compile` + API tests for all endpoints. Frontend: `tsc --noEmit`. Manual verification of full flow.

---

**注：** Task 8-15 的详细代码和步骤在实际执行时根据技术选型结果（DokFlow SDK 具体 API）在 implementer subagent prompt 中展开。任务难度递增，Task 13 (PdfViewer) 是最大最核心的前端组件。
