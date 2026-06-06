package com.yang.lblogserver.auth.mapper;
import com.yang.lblogserver.auth.domain.PdfProgress;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PdfProgressMapper {
    int upsert(PdfProgress prog);
    PdfProgress selectByPdfUser(@Param("pdfId") Long pdfId, @Param("userId") Long userId);
}
