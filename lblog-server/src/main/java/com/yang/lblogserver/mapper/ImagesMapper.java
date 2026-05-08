package com.yang.lblogserver.mapper;

import com.yang.lblogserver.domain.Images;
import com.yang.lblogserver.vo.admin.AdminImageVO;
import com.yang.lblogserver.vo.admin.ImageStatisticsVO;
import com.yang.lblogserver.vo.admin.ImageUsageVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ImagesMapper {

    int insertImage(Images image);

    Images selectById(@Param("id") Long id);

    Images selectByMd5(@Param("md5") String md5);

    Images selectByUrl(@Param("url") String url);

    List<Images> selectByCreatedBy(@Param("createdBy") Long createdBy,
                                   @Param("offset") int offset,
                                   @Param("limit") int limit);

    int countByCreatedBy(@Param("createdBy") Long createdBy);

    List<Images> selectUnreferenced(@Param("createdBy") Long createdBy,
                                    @Param("offset") int offset,
                                    @Param("limit") int limit);

    int countUnreferenced(@Param("createdBy") Long createdBy);

    int softDeleteById(@Param("id") Long id);

    // ==================== 管理端 ====================

    /** 增强列表查询（含 usageCount，支持排序/筛选/分页） */
    List<AdminImageVO> selectAdminList(@Param("keyword") String keyword,
                                       @Param("status") String status,
                                       @Param("sort") String sort,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    /** 增强列表计数（同条件） */
    int countAdminList(@Param("keyword") String keyword,
                       @Param("status") String status);

    /** 查询图片引用详情（含引用标题） */
    List<ImageUsageVO> selectImageUsages(@Param("imageId") Long imageId);

    /** 图片统计概览 */
    ImageStatisticsVO selectImageStatistics(@Param("cleanupDays") int cleanupDays);

    /** 查询清理候选图片（未引用且超过指定天数） */
    List<Images> selectCleanupCandidates(@Param("beforeDays") int beforeDays);

    /** 根据ID查询图片（含已删除的） */
    Images selectByIdRaw(@Param("id") Long id);

    /** 物理删除图片记录 */
    int hardDeleteById(@Param("id") Long id);
}
