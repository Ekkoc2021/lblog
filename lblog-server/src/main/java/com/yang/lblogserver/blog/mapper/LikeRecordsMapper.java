package com.yang.lblogserver.blog.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LikeRecordsMapper {

    int insert(@Param("postId") Long postId, @Param("visitorId") String visitorId);

    int deleteByPostIdAndVisitorId(@Param("postId") Long postId, @Param("visitorId") String visitorId);

    int existsByPostIdAndVisitorId(@Param("postId") Long postId, @Param("visitorId") String visitorId);
}
