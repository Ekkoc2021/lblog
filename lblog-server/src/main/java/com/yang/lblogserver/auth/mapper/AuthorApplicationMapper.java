package com.yang.lblogserver.auth.mapper;

import com.yang.lblogserver.auth.domain.AuthorApplication;
import com.yang.lblogserver.auth.vo.ApplicationVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AuthorApplicationMapper {

    int insert(AuthorApplication app);

    AuthorApplication selectByUserId(@Param("userId") Long userId);

    AuthorApplication selectById(@Param("id") Long id);

    int updateReview(@Param("id") Long id, @Param("status") Integer status,
                     @Param("feedback") String feedback, @Param("reviewedBy") Long reviewedBy);

    List<ApplicationVO> selectApplicationList(@Param("status") Integer status,
                                              @Param("keyword") String keyword,
                                              @Param("offset") int offset,
                                              @Param("limit") int limit);

    int countApplicationList(@Param("status") Integer status,
                             @Param("keyword") String keyword);
}
