package com.yang.lblogserver.service;

import com.yang.lblogserver.common.PageResult;
import com.yang.lblogserver.vo.CommentVO;
import com.yang.lblogserver.vo.admin.AdminCommentVO;

import java.util.List;

public interface CommentsService {

    Long createComment(Long postId, String content, Long parentId, Long userId,
                       String authorName, String authorAvatar, String ip);

    /** 分页获取顶级评论。currentUserId 非空时同时返回该用户自己的评论（不论审核状态） */
    PageResult<CommentVO> getPostComments(Long postId, int page, int pageSize, String sort, Long currentUserId);

    /** 分页获取子回复。currentUserId 非空时同时返回该用户自己的评论 */
    PageResult<CommentVO> getReplies(Long rootId, int page, int pageSize, Long currentUserId);

    List<CommentVO> getAdminCommentList(Integer status);

    PageResult<AdminCommentVO> getAdminCommentPage(int page, int pageSize, Integer status,
                                                    String keyword, Long postId);

    void updateStatus(Long id, Integer status);

    void deleteComment(Long id);
}
