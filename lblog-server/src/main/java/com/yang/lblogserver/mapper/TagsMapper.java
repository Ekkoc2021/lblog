package com.yang.lblogserver.mapper;

import com.yang.lblogserver.domain.Tags;
import com.yang.lblogserver.vo.TagVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TagsMapper {

    List<TagVO> selectTagsWithCount(@Param("limit") int limit);

    List<Tags> selectBatchIds(List<Long> ids);
}
