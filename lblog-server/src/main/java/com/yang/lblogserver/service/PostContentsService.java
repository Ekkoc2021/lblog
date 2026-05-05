package com.yang.lblogserver.service;

import com.yang.lblogserver.domain.PostContents;

/**
 * 文章内容存储服务。
 * 当前基于 MySQL + MyBatis，可替换实现以支持 MongoDB / ES 等。
 */
public interface PostContentsService {

    /** 根据文章 ID 查询正文 */
    PostContents getByPostId(Long postId);

    /** 新建正文记录 */
    void save(Long postId, String body, String format);

    /** 更新正文，不存在则插入（upsert） */
    void saveOrUpdate(Long postId, String body);
}
