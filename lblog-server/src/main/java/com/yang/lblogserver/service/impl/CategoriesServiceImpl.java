package com.yang.lblogserver.service.impl;

import com.yang.lblogserver.domain.Categories;
import com.yang.lblogserver.mapper.CategoriesMapper;
import com.yang.lblogserver.service.CategoriesService;
import com.yang.lblogserver.vo.response.CategoryVO;
import com.yang.lblogserver.vo.admin.CreateCategoryRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoriesServiceImpl implements CategoriesService {

    private final CategoriesMapper categoriesMapper;

    public CategoriesServiceImpl(CategoriesMapper categoriesMapper) {
        this.categoriesMapper = categoriesMapper;
    }

    @Override
    public List<CategoryVO> getCategoryList(int limit, Long createdBy) {
        return categoriesMapper.selectCategoriesWithCount(limit, createdBy);
    }

    // ---- Admin ----

    @Override
    public boolean checkSlug(String slug, Long excludeId) {
        return categoriesMapper.countBySlug(slug, excludeId) == 0;
    }

    @Override
    public Long createCategory(CreateCategoryRequest req, Long createdBy) {
        Categories category = new Categories();
        category.setName(req.getName());
        category.setSlug(req.getSlug());
        category.setDescription(req.getDescription());
        category.setParentId(req.getParentId());
        category.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
        category.setCreatedBy(createdBy);
        categoriesMapper.insertCategory(category);
        return category.getId();
    }

    @Override
    public void updateCategory(Long id, CreateCategoryRequest req) {
        Categories category = new Categories();
        category.setId(id);
        category.setName(req.getName());
        category.setSlug(req.getSlug());
        category.setDescription(req.getDescription());
        category.setParentId(req.getParentId());
        category.setSortOrder(req.getSortOrder());
        categoriesMapper.updateCategory(category);
    }

    @Override
    public void deleteCategory(Long id) {
        categoriesMapper.softDeleteCategory(id);
    }

    @Override
    public CategoryVO getCategoryById(Long id) {
        Categories cat = categoriesMapper.selectById(id);
        if (cat == null) return null;
        CategoryVO vo = new CategoryVO();
        vo.setId(cat.getId());
        vo.setName(cat.getName());
        vo.setSlug(cat.getSlug());
        vo.setParentId(cat.getParentId());
        vo.setDescription(cat.getDescription());
        vo.setSortOrder(cat.getSortOrder());
        return vo;
    }
}
