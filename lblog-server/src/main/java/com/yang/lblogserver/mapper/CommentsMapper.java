package com.yang.lblogserver.mapper;

import com.yang.lblogserver.domain.Comments;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CommentsMapper {

    int insert(Comments comment);

    List<Comments> selectRootByPostId(@Param("postId") Long postId,
                                       @Param("limit") int limit,
                                       @Param("offset") int offset,
                                       @Param("sort") String sort,
                                       @Param("currentUserId") Long currentUserId);

    int countRootByPostId(@Param("postId") Long postId,
                           @Param("currentUserId") Long currentUserId);

    List<Comments> selectRepliesByRootId(@Param("rootId") Long rootId,
                                          @Param("limit") int limit,
                                          @Param("offset") int offset,
                                          @Param("currentUserId") Long currentUserId);

    int countRepliesByRootId(@Param("rootId") Long rootId,
                              @Param("currentUserId") Long currentUserId);

    Comments selectById(@Param("id") Long id);

    List<Comments> selectAdminList(@Param("status") Integer status);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    int softDelete(@Param("id") Long id);

    @Update("UPDATE comments SET reply_count = reply_count + 1 WHERE id = #{rootId}")
    int incrementReplyCount(@Param("rootId") Long rootId);

    @Update("UPDATE comments SET reply_count = reply_count - 1 WHERE id = #{rootId} AND reply_count > 0")
    int decrementReplyCount(@Param("rootId") Long rootId);
}
