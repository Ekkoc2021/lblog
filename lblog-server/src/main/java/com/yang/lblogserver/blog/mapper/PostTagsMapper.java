package com.yang.lblogserver.blog.mapper;

import com.yang.lblogserver.blog.domain.PostTags;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PostTagsMapper {

    List<PostTags> selectByPostIds(List<Long> postIds);

    // ---- Admin ----

    int deleteByPostId(@Param("postId") Long postId);

    int insertBatch(List<PostTags> list);
}
