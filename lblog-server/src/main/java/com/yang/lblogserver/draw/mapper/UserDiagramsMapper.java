package com.yang.lblogserver.draw.mapper;

import com.yang.lblogserver.draw.domain.UserDiagram;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserDiagramsMapper {

    int insert(UserDiagram diagram);

    UserDiagram selectById(@Param("id") Long id);

    List<UserDiagram> selectList(@Param("userId") Long userId,
                                 @Param("keyword") String keyword);

    int updateContent(UserDiagram diagram);

    int updateMeta(UserDiagram diagram);

    int softDelete(@Param("id") Long id, @Param("userId") Long userId);

    int restore(@Param("id") Long id, @Param("userId") Long userId);
}
