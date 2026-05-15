package com.yang.lblogserver.image.mapper;

import com.yang.lblogserver.image.domain.ImageUsage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ImageUsageMapper {

    int insert(ImageUsage usage);

    int deleteByRef(@Param("refType") String refType, @Param("refId") Long refId);

    int deleteByRefAndField(@Param("refType") String refType,
                            @Param("refId") Long refId,
                            @Param("field") String field);

    List<ImageUsage> selectByRef(@Param("refType") String refType, @Param("refId") Long refId);

    List<ImageUsage> selectByImageId(@Param("imageId") Long imageId);

    int existsByImageId(@Param("imageId") Long imageId);
}
