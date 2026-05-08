package com.yang.lblogserver.mapper;

import com.yang.lblogserver.domain.SiteConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SiteConfigMapper {

    String selectConfigValue(@Param("configKey") String configKey);

    int updateConfigValue(@Param("configKey") String configKey, @Param("configValue") String configValue);

    List<SiteConfig> selectAll();

    int deleteByKey(@Param("configKey") String configKey);
}
