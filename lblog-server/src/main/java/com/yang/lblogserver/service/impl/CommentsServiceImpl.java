package com.yang.lblogserver.service.impl;

import com.yang.lblogserver.common.PageResult;
import com.yang.lblogserver.domain.Comments;
import com.yang.lblogserver.domain.Posts;
import com.yang.lblogserver.mapper.CommentsMapper;
import com.yang.lblogserver.mapper.PostsMapper;
import com.yang.lblogserver.service.CommentsService;
import com.yang.lblogserver.vo.CommentVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CommentsServiceImpl implements CommentsService {

    private final CommentsMapper commentsMapper;
    private final PostsMapper postsMapper;

    public CommentsServiceImpl(CommentsMapper commentsMapper, PostsMapper postsMapper) {
        this.commentsMapper = commentsMapper;
        this.postsMapper = postsMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createComment(Long postId, String content, Long parentId, Long userId,
                              String authorName, String authorAvatar, String ip) {
        Posts post = postsMapper.selectById(postId);
        if (post == null) {
            throw new IllegalArgumentException("文章不存在");
        }
        if (post.getCommentEnable() != null && post.getCommentEnable() == 0) {
            throw new IllegalArgumentException("该文章已关闭评论");
        }

        Long rootId = null;
        Long replyToUid = null;
        String replyToName = null;

        if (parentId != null) {
            Comments parent = commentsMapper.selectById(parentId);
            if (parent == null) {
                throw new IllegalArgumentException("被回复的评论不存在");
            }
            rootId = parent.getRootId() != null ? parent.getRootId() : parent.getId();
            // 只有回复的是另一条回复才记录 @ 目标（直接回复根评论不需要 @ 前缀）
            if (parent.getParentId() != null) {
                replyToUid = parent.getUserId();
                replyToName = parent.getAuthorName();
            }
        }

        Comments comment = new Comments();
        comment.setPostId(postId);
        comment.setParentId(parentId);
        comment.setRootId(rootId);
        comment.setUserId(userId);
        comment.setAuthorName(authorName);
        comment.setAuthorAvatar(authorAvatar);
        comment.setReplyToUid(replyToUid);
        comment.setReplyToName(replyToName);
        comment.setContent(content);
        comment.setIpAddress(ip);
        comment.setStatus(0);
        commentsMapper.insert(comment);

        // 回复时递增根评论的 reply_count
        if (rootId != null) {
            commentsMapper.incrementReplyCount(rootId);
        }

        return comment.getId();
    }

    @Override
    public PageResult<CommentVO> getPostComments(Long postId, int page, int pageSize, String sort, Long currentUserId) {
        int offset = (page - 1) * pageSize;
        List<Comments> roots = commentsMapper.selectRootByPostId(postId, pageSize, offset, sort, currentUserId);
        int total = commentsMapper.countRootByPostId(postId, currentUserId);

        List<CommentVO> voList = roots.stream().map(this::toVO).toList();
        return PageResult.of(page, pageSize, total, voList);
    }

    @Override
    public PageResult<CommentVO> getReplies(Long rootId, int page, int pageSize, Long currentUserId) {
        int offset = (page - 1) * pageSize;
        List<Comments> replies = commentsMapper.selectRepliesByRootId(rootId, pageSize, offset, currentUserId);
        int total = commentsMapper.countRepliesByRootId(rootId, currentUserId);

        List<CommentVO> voList = replies.stream().map(this::toVO).toList();
        return PageResult.of(page, pageSize, total, voList);
    }

    @Override
    public List<CommentVO> getAdminCommentList(Integer status) {
        return commentsMapper.selectAdminList(status).stream().map(this::toVO).toList();
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        commentsMapper.updateStatus(id, status);
    }

    @Override
    public void deleteComment(Long id) {
        Comments c = commentsMapper.selectById(id);
        if (c != null && c.getRootId() != null) {
            commentsMapper.decrementReplyCount(c.getRootId());
        }
        commentsMapper.softDelete(id);
    }

    // ========== 内部方法 ==========

    private CommentVO toVO(Comments c) {
        CommentVO vo = new CommentVO();
        vo.setId(c.getId());
        vo.setPostId(c.getPostId());
        vo.setContent(c.getContent());
        vo.setLikeCount(c.getLikeCount());
        vo.setReplyCount(c.getReplyCount());
        vo.setCreatedAt(c.getCreatedAt());
        vo.setIpAddress(c.getIpAddress());
        vo.setStatus(c.getStatus());

        CommentVO.AuthorVO author = new CommentVO.AuthorVO();
        author.setId(c.getUserId());
        author.setNickname(c.getAuthorName());
        author.setAvatar(c.getAuthorAvatar());
        vo.setAuthor(author);

        if (c.getReplyToUid() != null) {
            CommentVO.ReplyToVO replyTo = new CommentVO.ReplyToVO();
            replyTo.setId(c.getReplyToUid());
            replyTo.setNickname(c.getReplyToName());
            vo.setReplyTo(replyTo);
        }
        return vo;
    }
}
