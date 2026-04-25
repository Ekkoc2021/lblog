package com.yang.lblogserver.mapper;

import com.yang.lblogserver.domain.Posts;
import com.yang.lblogserver.vo.HotPostVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PostsMapper {

    List<Posts> selectPostList(@Param("sort") String sort,
                               @Param("categoryId") Long categoryId,
                               @Param("tagId") Long tagId,
                               @Param("seriesId") Long seriesId,
                               @Param("keyword") String keyword);

    List<HotPostVO> selectHotPosts(@Param("limit") int limit);
}
