package com.yang.lblogserver.mapper;

import com.yang.lblogserver.domain.Tags;
import com.yang.lblogserver.vo.TagVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TagsMapper {

    List<TagVO> selectTagsWithCount(@Param("limit") int limit,
                                     @Param("createdBy") Long createdBy);

    List<Tags> selectBatchIds(List<Long> ids);

    // ---- Admin ----

    int insertTag(Tags tag);

    int updateTag(Tags tag);

    int softDeleteTag(@Param("id") Long id);

    Tags selectById(@Param("id") Long id);
}
