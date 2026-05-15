package com.yang.lblogserver.draw.service.impl;

import com.yang.lblogserver.common.PageResult;
import com.yang.lblogserver.draw.domain.UserDiagram;
import com.yang.lblogserver.draw.mapper.UserDiagramsMapper;
import com.yang.lblogserver.draw.service.UserDiagramsService;
import com.yang.lblogserver.draw.vo.DiagramDetailVO;
import com.yang.lblogserver.draw.vo.DiagramListVO;
import com.yang.lblogserver.draw.vo.SaveDiagramRequest;
import com.yang.lblogserver.draw.vo.UpdateMetaRequest;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserDiagramsServiceImpl implements UserDiagramsService {

    private final UserDiagramsMapper userDiagramsMapper;

    public UserDiagramsServiceImpl(UserDiagramsMapper userDiagramsMapper) {
        this.userDiagramsMapper = userDiagramsMapper;
    }

    @Override
    @Transactional
    public Long create(Long userId, SaveDiagramRequest request) {
        UserDiagram diagram = new UserDiagram();
        diagram.setUserId(userId);
        diagram.setTitle(request.getTitle());
        diagram.setDescription(request.getDescription());
        diagram.setTags(request.getTags());
        diagram.setXmlData(request.getXmlData());
        diagram.setThumbnail(request.getThumbnail());
        diagram.setFileSize(request.getFileSize());
        userDiagramsMapper.insert(diagram);
        return diagram.getId();
    }

    @Override
    public PageResult<DiagramListVO> list(Long userId, int page, int pageSize, String keyword) {
        PageHelper.startPage(page, pageSize);
        List<UserDiagram> list = userDiagramsMapper.selectList(userId, keyword);
        PageInfo<UserDiagram> pageInfo = new PageInfo<>(list);
        List<DiagramListVO> voList = list.stream().map(d -> {
            DiagramListVO vo = new DiagramListVO();
            vo.setId(d.getId());
            vo.setTitle(d.getTitle());
            vo.setDescription(d.getDescription());
            vo.setTags(d.getTags());
            vo.setThumbnail(d.getThumbnail());
            vo.setFileSize(d.getFileSize());
            vo.setCreatedAt(d.getCreatedAt());
            vo.setUpdatedAt(d.getUpdatedAt());
            return vo;
        }).collect(Collectors.toList());
        return PageResult.of(page, pageSize, (int) pageInfo.getTotal(), voList);
    }

    @Override
    public DiagramDetailVO getById(Long id, Long userId) {
        UserDiagram diagram = userDiagramsMapper.selectById(id);
        if (diagram == null) return null;
        if (!diagram.getUserId().equals(userId)) return null;
        DiagramDetailVO vo = new DiagramDetailVO();
        vo.setId(diagram.getId());
        vo.setUserId(diagram.getUserId());
        vo.setTitle(diagram.getTitle());
        vo.setDescription(diagram.getDescription());
        vo.setTags(diagram.getTags());
        vo.setXmlData(diagram.getXmlData());
        vo.setThumbnail(diagram.getThumbnail());
        vo.setFileSize(diagram.getFileSize());
        vo.setCreatedAt(diagram.getCreatedAt());
        vo.setUpdatedAt(diagram.getUpdatedAt());
        return vo;
    }

    @Override
    @Transactional
    public void updateContent(Long id, Long userId, SaveDiagramRequest request) {
        UserDiagram diagram = new UserDiagram();
        diagram.setId(id);
        diagram.setUserId(userId);
        diagram.setTitle(request.getTitle());
        diagram.setDescription(request.getDescription());
        diagram.setTags(request.getTags());
        diagram.setXmlData(request.getXmlData());
        diagram.setThumbnail(request.getThumbnail());
        diagram.setFileSize(request.getFileSize());
        userDiagramsMapper.updateContent(diagram);
    }

    @Override
    @Transactional
    public void updateMeta(Long id, Long userId, UpdateMetaRequest request) {
        UserDiagram diagram = new UserDiagram();
        diagram.setId(id);
        diagram.setUserId(userId);
        diagram.setTitle(request.getTitle());
        diagram.setDescription(request.getDescription());
        diagram.setTags(request.getTags());
        userDiagramsMapper.updateMeta(diagram);
    }

    @Override
    @Transactional
    public void delete(Long id, Long userId) {
        userDiagramsMapper.softDelete(id, userId);
    }
}
