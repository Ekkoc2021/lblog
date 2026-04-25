package com.yang.lblogserver.service.impl;

import com.yang.lblogserver.mapper.SeriesMapper;
import com.yang.lblogserver.service.SeriesService;
import com.yang.lblogserver.vo.SeriesVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SeriesServiceImpl implements SeriesService {

    private final SeriesMapper seriesMapper;

    public SeriesServiceImpl(SeriesMapper seriesMapper) {
        this.seriesMapper = seriesMapper;
    }

    @Override
    public List<SeriesVO> getSeriesList(int limit, Long categoryId) {
        return seriesMapper.selectSeriesWithCount(limit, categoryId);
    }
}
