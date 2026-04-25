package com.yang.lblogserver.service;

import com.yang.lblogserver.vo.SeriesVO;

import java.util.List;

public interface SeriesService {

    List<SeriesVO> getSeriesList(int limit, Long categoryId);
}
