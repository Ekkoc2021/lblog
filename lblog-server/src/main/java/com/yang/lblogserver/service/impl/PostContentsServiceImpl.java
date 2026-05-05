package com.yang.lblogserver.service.impl;

import com.yang.lblogserver.domain.PostContents;
import com.yang.lblogserver.mapper.PostContentsMapper;
import com.yang.lblogserver.service.PostContentsService;
import org.springframework.stereotype.Service;

@Service
public class PostContentsServiceImpl implements PostContentsService {

    private final PostContentsMapper postContentsMapper;

    public PostContentsServiceImpl(PostContentsMapper postContentsMapper) {
        this.postContentsMapper = postContentsMapper;
    }

    @Override
    public PostContents getByPostId(Long postId) {
        return postContentsMapper.selectByPostId(postId);
    }

    @Override
    public void save(Long postId, String body, String format) {
        PostContents contents = new PostContents();
        contents.setPostId(postId);
        contents.setBody(body);
        contents.setFormat(format);
        postContentsMapper.insert(contents);
    }

    @Override
    public void saveOrUpdate(Long postId, String body) {
        PostContents contents = new PostContents();
        contents.setPostId(postId);
        contents.setBody(body);
        int affected = postContentsMapper.updateByPostId(contents);
        if (affected == 0) {
            contents.setFormat("markdown");
            postContentsMapper.insert(contents);
        }
    }
}
