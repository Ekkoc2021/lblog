package com.yang.lblogserver.auth.mapper;

import com.yang.lblogserver.auth.domain.PdfUserQuota;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface PdfUserQuotaMapper {
    PdfUserQuota selectByUserId(@Param("userId") Long userId);
    int insertDefault(@Param("userId") Long userId);
    int updateQuota(@Param("userId") Long userId, @Param("quotaBytes") Long quotaBytes);
    int updateAllowUpload(@Param("userId") Long userId, @Param("allowUpload") Integer allowUpload);

    /** 管理列表 — 所有上传过 PDF 的用户 + 已配额的用户的汇总数据 */
    List<Map<String, Object>> selectUserStats();
}
