package com.yang.lblogserver.service;

import com.yang.lblogserver.vo.CategoryVO;
import com.yang.lblogserver.vo.admin.CreateCategoryRequest;

import java.util.List;

public interface CategoriesService {

    List<CategoryVO> getCategoryList(int limit, Long createdBy);

    // ---- Admin ----

    Long createCategory(CreateCategoryRequest req, Long createdBy);

    void updateCategory(Long id, CreateCategoryRequest req);

    void deleteCategory(Long id);

    CategoryVO getCategoryById(Long id);
}

