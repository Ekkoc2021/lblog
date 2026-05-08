package com.yang.lblogserver.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SiteConfigMapper {

    String selectConfigValue(@Param("configKey") String configKey);

    int updateConfigValue(@Param("configKey") String configKey, @Param("configValue") String configValue);
}
