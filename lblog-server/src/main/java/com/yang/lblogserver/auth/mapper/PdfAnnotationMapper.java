package com.yang.lblogserver.auth.mapper;
import com.yang.lblogserver.auth.domain.PdfAnnotation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PdfAnnotationMapper {
    int upsert(PdfAnnotation ann);
    PdfAnnotation selectByPdfPageUser(@Param("pdfId") Long pdfId, @Param("pageNum") Integer pageNum, @Param("userId") Long userId);
    int deleteByPdfId(@Param("pdfId") Long pdfId);
}
