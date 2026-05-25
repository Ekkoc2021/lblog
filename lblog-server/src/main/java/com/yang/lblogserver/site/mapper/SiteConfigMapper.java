package com.yang.lblogserver.site.mapper;

import com.yang.lblogserver.site.domain.SiteConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SiteConfigMapper {

    String selectConfigValue(@Param("configKey") String configKey);

    int updateConfigValue(@Param("configKey") String configKey, @Param("configValue") String configValue);

    List<SiteConfig> selectAll();

    int insertConfig(@Param("configKey") String configKey, @Param("configValue") String configValue);

    int deleteByKey(@Param("configKey") String configKey);
}
