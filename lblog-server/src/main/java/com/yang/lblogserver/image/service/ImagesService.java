package com.yang.lblogserver.image.service;

import com.yang.lblogserver.common.PageResult;
import com.yang.lblogserver.image.domain.Images;

public interface ImagesService {

    /** 分页查询作者图片列表 */
    PageResult<Images> getImageList(Long createdBy, int page, int pageSize);

    /** 分页查询未引用图片 */
    PageResult<Images> getUnreferencedImages(Long createdBy, int page, int pageSize);

    /** 删除图片（检查所有权 + 引用，已被引用不可删） */
    void deleteImage(Long id, Long userId);

    /** 记录图片入库，返回 imageId */
    Long recordImage(String url, String storagePath, String originalName, String mimeType,
                     long fileSize, Integer width, Integer height, String md5, Long createdBy);

    /** 根据 md5 查找图片 */
    Images findByMd5(String md5);

    /** 根据 URL 查找图片 */
    Images findByUrl(String url);
}
