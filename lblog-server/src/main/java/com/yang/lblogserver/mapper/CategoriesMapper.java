package com.yang.lblogserver.mapper;

import com.yang.lblogserver.domain.Categories;
import com.yang.lblogserver.vo.CategoryVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CategoriesMapper {

    List<CategoryVO> selectCategoriesWithCount();

    List<Categories> selectBatchIds(List<Long> ids);
}
