package com.yang.lblogserver.blog.mapper;

import com.yang.lblogserver.blog.domain.PostContents;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PostContentsMapper {

    PostContents selectByPostId(@Param("postId") Long postId);

    // ---- Admin ----

    int insert(PostContents contents);

    int updateByPostId(PostContents contents);
}
