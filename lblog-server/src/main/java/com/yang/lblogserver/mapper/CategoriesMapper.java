package com.yang.lblogserver.mapper;

import com.yang.lblogserver.domain.Categories;
import com.yang.lblogserver.vo.CategoryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CategoriesMapper {

    List<CategoryVO> selectCategoriesWithCount(@Param("limit") Integer limit);

    List<Categories> selectBatchIds(List<Long> ids);

    // ---- Admin ----

    int insertCategory(Categories category);

    int updateCategory(Categories category);

    int softDeleteCategory(@Param("id") Long id);

    int countPostsByCategoryId(@Param("categoryId") Long categoryId);

    Categories selectById(@Param("id") Long id);
}
