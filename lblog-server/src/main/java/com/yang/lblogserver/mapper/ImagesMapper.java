package com.yang.lblogserver.mapper;

import com.yang.lblogserver.domain.Images;
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
}
