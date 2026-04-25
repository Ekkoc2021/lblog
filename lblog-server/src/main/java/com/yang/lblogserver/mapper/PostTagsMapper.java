package com.yang.lblogserver.mapper;

import com.yang.lblogserver.domain.PostTags;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PostTagsMapper {

    List<PostTags> selectByPostIds(List<Long> postIds);
}
