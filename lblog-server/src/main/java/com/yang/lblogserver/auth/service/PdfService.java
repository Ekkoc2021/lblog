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
    private final PdfStorage pdfStorage;

    public PdfService(PdfFileMapper fileMapper, PdfFolderMapper folderMapper,
                      PdfAnnotationMapper annotationMapper, PdfBookmarkMapper bookmarkMapper,
                      PdfProgressMapper progressMapper, PdfStorage pdfStorage) {
        this.fileMapper = fileMapper;
        this.folderMapper = folderMapper;
        this.annotationMapper = annotationMapper;
        this.bookmarkMapper = bookmarkMapper;
        this.progressMapper = progressMapper;
        this.pdfStorage = pdfStorage;
    }

    /** Upload PDF with validation */
    @Transactional
    public PdfFile upload(Long userId, MultipartFile file, Long folderId) throws IOException {
        String contentType = file.getContentType();
        if (contentType == null || !"application/pdf".equals(contentType)) {
            throw new IllegalArgumentException("仅支持 PDF 格式");
        }
        if (file.isEmpty()) {
            throw new IllegalArgumentException("文件为空");
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
        fileMapper.insert(pdfFile);
        return pdfFile;
    }

    /** Update PDF total pages (called after PDF.js reads page count) */
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

    /** Rename or move file */
    public void updateFile(Long id, String originalName, Long folderId) {
        fileMapper.update(id, originalName, folderId);
    }

    /** Delete file: physical + cascade annotations/bookmarks/progress */
    @Transactional
    public void deleteFile(Long id) {
        PdfFile f = fileMapper.selectById(id);
        if (f != null) {
            try { pdfStorage.delete(f.getFilePath()); } catch (Exception ignored) {}
            annotationMapper.deleteByPdfId(id);
            bookmarkMapper.deleteByPdfId(id);
            fileMapper.delete(id);
        }
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
}
