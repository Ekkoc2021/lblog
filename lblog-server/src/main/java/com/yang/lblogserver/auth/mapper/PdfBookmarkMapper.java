package com.yang.lblogserver.auth.mapper;
import com.yang.lblogserver.auth.domain.PdfBookmark;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface PdfBookmarkMapper {
    int insert(PdfBookmark bm);
    List<PdfBookmark> selectByPdfUser(@Param("pdfId") Long pdfId, @Param("userId") Long userId);
    int update(@Param("id") Long id, @Param("label") String label, @Param("note") String note, @Param("userId") Long userId);
    int delete(@Param("id") Long id);
    int deleteByPdfId(@Param("pdfId") Long pdfId);
}
