package com.yang.lblogserver.auth.service;

import com.yang.lblogserver.auth.domain.*;
import com.yang.lblogserver.auth.mapper.*;
import com.yang.lblogserver.auth.vo.*;
import com.yang.lblogserver.storage.PdfStorage;
import com.yang.lblogserver.storage.StorageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PdfService {

    private final PdfFileMapper fileMapper;
    private final PdfFolderMapper folderMapper;
    private final PdfAnnotationMapper annotationMapper;
    private final PdfBookmarkMapper bookmarkMapper;
    private final PdfProgressMapper progressMapper;
    private final PdfUserQuotaMapper quotaMapper;
    private final PdfQuotaHelper quotaHelper;
    private final PdfStorage pdfStorage;

    public PdfService(PdfFileMapper fileMapper, PdfFolderMapper folderMapper,
                      PdfAnnotationMapper annotationMapper, PdfBookmarkMapper bookmarkMapper,
                      PdfProgressMapper progressMapper, PdfUserQuotaMapper quotaMapper,
                      PdfQuotaHelper quotaHelper, PdfStorage pdfStorage) {
        this.fileMapper = fileMapper;
        this.folderMapper = folderMapper;
        this.annotationMapper = annotationMapper;
        this.bookmarkMapper = bookmarkMapper;
        this.progressMapper = progressMapper;
        this.quotaMapper = quotaMapper;
        this.quotaHelper = quotaHelper;
        this.pdfStorage = pdfStorage;
    }

    /** Upload PDF with validation + quota check */
    @Transactional
    public PdfFile upload(Long userId, MultipartFile file, Long folderId, String sourceType) throws IOException {
        String contentType = file.getContentType();
        if (contentType == null || !"application/pdf".equals(contentType)) {
            throw new IllegalArgumentException("仅支持 PDF 格式");
        }
        if (file.isEmpty()) {
            throw new IllegalArgumentException("文件为空");
        }

        // 配额检查
        PdfUserQuota quota = quotaMapper.selectByUserId(userId);
        if (quota == null) {
            quotaHelper.ensureDefaultQuota(userId);
            throw new IllegalArgumentException("请先申请 PDF 上传权限");
        }
        if (quota.getAllowUpload() == null || quota.getAllowUpload() == 0) {
            throw new IllegalArgumentException("PDF 上传权限未开启，请联系管理员");
        }
        long currentTotal = fileMapper.sumSizeByUser(userId);
        if (currentTotal + file.getSize() > quota.getQuotaBytes()) {
            throw new IllegalArgumentException(String.format(
                "PDF 存储空间已满 (已用 %.1f MB / 配额 %.1f MB)",
                currentTotal / 1048576.0, quota.getQuotaBytes() / 1048576.0));
        }
        String originalName = file.getOriginalFilename();
        String storedName = UUID.randomUUID().toString() + ".pdf";
        StorageResult result = pdfStorage.store(file.getInputStream(), storedName, file.getSize(), contentType);

        PdfFile pdfFile = new PdfFile();
        pdfFile.setUserId(userId);
        pdfFile.setFolderId(folderId);
        pdfFile.setFilename(storedName);
        pdfFile.setOriginalName(originalName != null ? originalName : "untitled.pdf");
        pdfFile.setFileSize(file.getSize());
        pdfFile.setFilePath(result.getStoragePath());
        pdfFile.setTotalPages(0);
        pdfFile.setSourceType(sourceType != null ? sourceType : "UPLOAD");
        fileMapper.insert(pdfFile);
        return pdfFile;
    }

    /** Create a book entry without uploading a file */
    public PdfFile createMetadata(Long userId, String name, Long folderId) {
        PdfFile pdfFile = new PdfFile();
        pdfFile.setUserId(userId);
        pdfFile.setFolderId(folderId);
        pdfFile.setFilename("");
        pdfFile.setOriginalName(name);
        pdfFile.setFileSize(0L);
        pdfFile.setFilePath("");
        pdfFile.setTotalPages(0);
        pdfFile.setSourceType("LOCAL");
        fileMapper.insert(pdfFile);
        return pdfFile;
    }

    /** Upload file to existing LOCAL book, converting it to UPLOAD */
    @Transactional
    public PdfFile uploadToExisting(Long id, Long userId, MultipartFile file) throws IOException {
        PdfFile f = fileMapper.selectById(id);
        if (f == null || !f.getUserId().equals(userId)) throw new IllegalArgumentException("文件不存在");

        // Quota check (same as upload)
        PdfUserQuota quota = quotaMapper.selectByUserId(userId);
        if (quota == null) {
            quotaHelper.ensureDefaultQuota(userId);
            throw new IllegalArgumentException("请先申请 PDF 上传权限");
        }
        if (quota.getAllowUpload() == null || quota.getAllowUpload() == 0) {
            throw new IllegalArgumentException("PDF 上传权限未开启，请联系管理员");
        }
        long currentTotal = fileMapper.sumSizeByUser(userId);
        if (currentTotal + file.getSize() > quota.getQuotaBytes()) {
            throw new IllegalArgumentException(String.format(
                "PDF 存储空间已满 (已用 %.1f MB / 配额 %.1f MB)",
                currentTotal / 1048576.0, quota.getQuotaBytes() / 1048576.0));
        }

        String storedName = UUID.randomUUID().toString() + ".pdf";
        StorageResult result = pdfStorage.store(file.getInputStream(), storedName, file.getSize(), "application/pdf");

        fileMapper.updateFileWithSource(id, file.getOriginalFilename(), storedName, file.getSize(), result.getStoragePath());
        f.setOriginalName(file.getOriginalFilename());
        f.setFilename(storedName);
        f.setFileSize(file.getSize());
        f.setFilePath(result.getStoragePath());
        f.setSourceType("UPLOAD");
        return f;
    }

    /** Update PDF total pages (called from bridge, no user context) */
    public void updateTotalPages(Long fileId, int totalPages) {
        fileMapper.updateTotalPages(fileId, totalPages);
    }

    /** List files in a folder (or root) */
    public List<PdfFileVO> getFiles(Long userId, Long folderId) {
        return fileMapper.selectByUserAndFolder(userId, folderId);
    }

    /** Get single file, verify ownership */
    public PdfFile getFile(Long id, Long userId) {
        PdfFile f = fileMapper.selectById(id);
        if (f == null) throw new IllegalArgumentException("文件不存在");
        if (!f.getUserId().equals(userId)) throw new IllegalArgumentException("文件不存在");
        return f;
    }

    /** Get file path for download (no ownership check here, caller does it) */
    public PdfFile getFileById(Long id) {
        PdfFile f = fileMapper.selectById(id);
        if (f == null) throw new IllegalArgumentException("文件不存在");
        return f;
    }

    /** Rename or move file (ownership verified by controller) */
    public void updateFile(Long id, String originalName, Long folderId) {
        fileMapper.update(id, originalName, folderId);
    }

    /** Delete file: physical + cascade annotations/bookmarks/progress */
    @Transactional
    public void deleteFile(Long id, Long userId) {
        PdfFile f = fileMapper.selectById(id);
        if (f == null || !f.getUserId().equals(userId)) throw new IllegalArgumentException("文件不存在");
        try { pdfStorage.delete(f.getFilePath()); } catch (Exception ignored) {}
        annotationMapper.deleteByPdfId(id);
        bookmarkMapper.deleteByPdfId(id);
        fileMapper.delete(id);
    }

    /** Build folder tree for user */
    public List<PdfFolderVO> getFolderTree(Long userId) {
        List<PdfFolder> all = folderMapper.selectByUser(userId);
        Map<Long, PdfFolderVO> map = new LinkedHashMap<>();
        List<PdfFolderVO> roots = new ArrayList<>();
        for (PdfFolder f : all) {
            PdfFolderVO vo = toVO(f);
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

    private PdfFolderVO toVO(PdfFolder f) {
        PdfFolderVO vo = new PdfFolderVO();
        vo.setId(f.getId());
        vo.setParentId(f.getParentId());
        vo.setName(f.getName());
        vo.setSortOrder(f.getSortOrder());
        vo.setCreatedAt(f.getCreatedAt());
        return vo;
    }

    /** Create folder with cycle prevention */
    public PdfFolder createFolder(Long userId, String name, Long parentId) {
        if (parentId != null) {
            PdfFolder parent = folderMapper.selectById(parentId);
            if (parent == null || !parent.getUserId().equals(userId))
                throw new IllegalArgumentException("父文件夹不存在");
        }
        PdfFolder f = new PdfFolder();
        f.setUserId(userId);
        f.setName(name);
        f.setParentId(parentId);
        folderMapper.insert(f);
        return f;
    }

    /** Update folder: rename or move (prevent self-reference) */
    public void updateFolder(Long id, String name, Long parentId) {
        if (parentId != null && parentId.equals(id))
            throw new IllegalArgumentException("不能移动到自身");
        folderMapper.update(id, name, parentId);
    }

    /** Delete folder (DB foreign key ON DELETE CASCADE handles children; files get folder_id=NULL) */
    @Transactional
    public void deleteFolder(Long id) {
        folderMapper.delete(id);
    }

    // ---- Annotations ----

    /** Get annotations for a single page. Returns "[]" if none. */
    public String getAnnotation(Long pdfId, int pageNum, Long userId) {
        PdfAnnotation ann = annotationMapper.selectByPdfPageUser(pdfId, pageNum, userId);
        return ann != null ? ann.getData() : "[]";
    }

    /** Save/update annotations for a single page */
    public void saveAnnotation(Long pdfId, int pageNum, Long userId, String data) {
        PdfAnnotation ann = new PdfAnnotation();
        ann.setPdfId(pdfId);
        ann.setPageNum(pageNum);
        ann.setUserId(userId);
        ann.setData(data);
        annotationMapper.upsert(ann);
    }

    // ---- Bookmarks ----

    public List<PdfBookmark> getBookmarks(Long pdfId, Long userId) {
        return bookmarkMapper.selectByPdfUser(pdfId, userId);
    }

    public PdfBookmark addBookmark(Long pdfId, Long userId, int pageNum, String label, String note) {
        PdfBookmark bm = new PdfBookmark();
        bm.setPdfId(pdfId);
        bm.setUserId(userId);
        bm.setPageNum(pageNum);
        bm.setLabel(label);
        bm.setNote(note);
        bookmarkMapper.insert(bm);
        return bm;
    }

    public void updateBookmark(Long id, String label, String note, Long userId) {
        bookmarkMapper.update(id, label, note, userId);
    }

    public void deleteBookmark(Long id, Long userId) {
        bookmarkMapper.delete(id, userId);
    }

    // ---- Progress ----

    public PdfProgress getProgress(Long pdfId, Long userId) {
        PdfProgress p = progressMapper.selectByPdfUser(pdfId, userId);
        if (p == null) {
            p = new PdfProgress();
            p.setPdfId(pdfId);
            p.setUserId(userId);
            p.setPageNum(1);
            p.setScrollTop(0f);
        }
        return p;
    }

    public void saveProgress(Long pdfId, Long userId, int pageNum, Float scrollTop) {
        PdfProgress p = new PdfProgress();
        p.setPdfId(pdfId);
        p.setUserId(userId);
        p.setPageNum(pageNum);
        p.setScrollTop(scrollTop != null ? scrollTop : 0f);
        progressMapper.upsert(p);
    }

    // ---- Quota ----

    /** 当前用户用量统计 */
    public Map<String, Object> getUserStats(Long userId) {
        PdfUserQuota q = quotaMapper.selectByUserId(userId);
        long totalSize = fileMapper.sumSizeByUser(userId);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalSize", totalSize);
        stats.put("quotaBytes", q != null ? q.getQuotaBytes() : 524288000L);
        stats.put("allowUpload", q != null ? q.getAllowUpload() : 0);
        return stats;
    }

    /** 管理员：所有用户用量列表 */
    public List<Map<String, Object>> getUserStatsList() {
        return quotaMapper.selectUserStats();
    }

    /** 管理员：设置用户配额 */
    public void setUserQuota(Long userId, Long quotaBytes) {
        quotaMapper.updateQuota(userId, quotaBytes);
    }

    /** 管理员：设置用户上传开关 */
    public void setUserAllowUpload(Long userId, Integer allowUpload) {
        quotaMapper.updateAllowUpload(userId, allowUpload);
    }
}
