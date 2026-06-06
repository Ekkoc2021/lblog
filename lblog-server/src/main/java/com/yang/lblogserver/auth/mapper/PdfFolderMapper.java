package com.yang.lblogserver.auth.mapper;
import com.yang.lblogserver.auth.domain.PdfFolder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface PdfFolderMapper {
    int insert(PdfFolder folder);
    PdfFolder selectById(@Param("id") Long id);
    List<PdfFolder> selectByUser(@Param("userId") Long userId);
    int update(@Param("id") Long id, @Param("name") String name, @Param("parentId") Long parentId);
    int delete(@Param("id") Long id);
}
