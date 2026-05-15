package com.yang.lblogserver.blog.service;

import com.yang.lblogserver.blog.vo.CategoryVO;
import com.yang.lblogserver.blog.vo.admin.CreateCategoryRequest;

import java.util.List;

public interface CategoriesService {

    List<CategoryVO> getCategoryList(int limit, Long createdBy);

    // ---- Admin ----

    boolean checkSlug(String slug, Long excludeId);

    Long createCategory(CreateCategoryRequest req, Long createdBy);

    void updateCategory(Long id, CreateCategoryRequest req);

    void deleteCategory(Long id);

    CategoryVO getCategoryById(Long id);
}

