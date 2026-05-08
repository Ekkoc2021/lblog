package com.yang.lblogserver.service.impl;

import com.yang.lblogserver.common.PageResult;
import com.yang.lblogserver.domain.Images;
import com.yang.lblogserver.mapper.ImageUsageMapper;
import com.yang.lblogserver.mapper.ImagesMapper;
import com.yang.lblogserver.service.ImagesService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ImagesServiceImpl implements ImagesService {

    private final ImagesMapper imagesMapper;
    private final ImageUsageMapper imageUsageMapper;

    public ImagesServiceImpl(ImagesMapper imagesMapper, ImageUsageMapper imageUsageMapper) {
        this.imagesMapper = imagesMapper;
        this.imageUsageMapper = imageUsageMapper;
    }

    @Override
    public PageResult<Images> getImageList(Long createdBy, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<Images> list = imagesMapper.selectByCreatedBy(createdBy, offset, pageSize);
        int total = imagesMapper.countByCreatedBy(createdBy);
        return PageResult.of(page, pageSize, total, list);
    }

    @Override
    public PageResult<Images> getUnreferencedImages(Long createdBy, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<Images> list = imagesMapper.selectUnreferenced(createdBy, offset, pageSize);
        int total = imagesMapper.countUnreferenced(createdBy);
        return PageResult.of(page, pageSize, total, list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteImage(Long id, Long userId) {
        Images image = imagesMapper.selectById(id);
        if (image == null) {
            throw new IllegalArgumentException("图片不存在");
        }
        if (!userId.equals(image.getCreatedBy())) {
            throw new IllegalArgumentException("只能删除自己上传的图片");
        }
        if (imageUsageMapper.existsByImageId(id) > 0) {
            throw new IllegalArgumentException("该图片已被引用，无法删除");
        }
        imagesMapper.softDeleteById(id);
    }

    @Override
    public Long recordImage(String url, String storagePath, String originalName, String mimeType,
                            long fileSize, Integer width, Integer height, String md5, Long createdBy) {
        Images image = new Images();
        image.setUrl(url);
        image.setStoragePath(storagePath);
        image.setOriginalName(originalName);
        image.setMimeType(mimeType);
        image.setFileSize(fileSize);
        image.setWidth(width);
        image.setHeight(height);
        image.setMd5(md5);
        image.setCreatedBy(createdBy);
        imagesMapper.insertImage(image);
        return image.getId();
    }

    @Override
    public Images findByMd5(String md5) {
        return imagesMapper.selectByMd5(md5);
    }

    @Override
    public Images findByUrl(String url) {
        return imagesMapper.selectByUrl(url);
    }
}
