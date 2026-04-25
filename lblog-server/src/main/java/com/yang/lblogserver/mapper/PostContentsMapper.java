package com.yang.lblogserver.mapper;

import com.yang.lblogserver.domain.PostContents;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PostContentsMapper {

    PostContents selectByPostId(@Param("postId") Long postId);

    // ---- Admin ----

    int insert(PostContents contents);

    int updateByPostId(PostContents contents);
}
