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
    int update(@Param("id") Long id, @Param("originalName") String originalName, @Param("folderId") Long folderId);
    int updateFileWithSource(@Param("id") Long id, @Param("originalName") String originalName,
                             @Param("filename") String filename, @Param("fileSize") Long fileSize,
                             @Param("filePath") String filePath);
    int delete(@Param("id") Long id);
    int updateTotalPages(@Param("id") Long id, @Param("totalPages") Integer totalPages);
    long sumSizeByUser(@Param("userId") Long userId);
}
