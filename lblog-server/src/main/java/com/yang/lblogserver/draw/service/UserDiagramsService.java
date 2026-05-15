package com.yang.lblogserver.draw.service;

import com.yang.lblogserver.common.PageResult;
import com.yang.lblogserver.draw.domain.UserDiagram;
import com.yang.lblogserver.draw.vo.DiagramDetailVO;
import com.yang.lblogserver.draw.vo.DiagramListVO;
import com.yang.lblogserver.draw.vo.SaveDiagramRequest;
import com.yang.lblogserver.draw.vo.UpdateMetaRequest;

public interface UserDiagramsService {

    Long create(Long userId, SaveDiagramRequest request);

    PageResult<DiagramListVO> list(Long userId, int page, int pageSize, String keyword);

    DiagramDetailVO getById(Long id, Long userId);

    void updateContent(Long id, Long userId, SaveDiagramRequest request);

    void updateMeta(Long id, Long userId, UpdateMetaRequest request);

    void delete(Long id, Long userId);
}
