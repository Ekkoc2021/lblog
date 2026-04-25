package com.yang.lblogserver.service;

import com.yang.lblogserver.vo.CategoryVO;

import java.util.List;

public interface CategoriesService {

    List<CategoryVO> getCategoryList(int limit);
}
