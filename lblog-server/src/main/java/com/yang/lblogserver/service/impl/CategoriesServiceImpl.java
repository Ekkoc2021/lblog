package com.yang.lblogserver.service.impl;

import com.yang.lblogserver.mapper.CategoriesMapper;
import com.yang.lblogserver.service.CategoriesService;
import com.yang.lblogserver.vo.CategoryVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoriesServiceImpl implements CategoriesService {

    private final CategoriesMapper categoriesMapper;

    public CategoriesServiceImpl(CategoriesMapper categoriesMapper) {
        this.categoriesMapper = categoriesMapper;
    }

    @Override
    public List<CategoryVO> getCategoryList(int limit) {
        return categoriesMapper.selectCategoriesWithCount(limit);
    }
}
