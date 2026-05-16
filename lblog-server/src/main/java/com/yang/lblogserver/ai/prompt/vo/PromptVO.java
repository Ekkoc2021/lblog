package com.yang.lblogserver.ai.prompt.vo;

import com.yang.lblogserver.ai.prompt.domain.AiPrompt;
import lombok.Data;

import java.util.Date;

@Data
public class PromptVO {
    private Long id;
    private String module;
    private String promptKey;
    private String content;
    private Integer version;
    private Integer sortOrder;
    private String description;
    private Boolean isActive;
    private Date effectiveFrom;
    private Date effectiveTo;
    private String createdBy;
    private String updatedBy;
    private Date createdAt;
    private Date updatedAt;

    public static PromptVO from(AiPrompt p) {
        if (p == null) return null;
        PromptVO vo = new PromptVO();
        vo.setId(p.getId());
        vo.setModule(p.getModule());
        vo.setPromptKey(p.getPromptKey());
        vo.setContent(p.getContent());
        vo.setVersion(p.getVersion());
        vo.setSortOrder(p.getSortOrder());
        vo.setDescription(p.getDescription());
        vo.setIsActive(p.getIsActive());
        vo.setEffectiveFrom(p.getEffectiveFrom());
        vo.setEffectiveTo(p.getEffectiveTo());
        vo.setCreatedBy(p.getCreatedBy());
        vo.setUpdatedBy(p.getUpdatedBy());
        vo.setCreatedAt(p.getCreatedAt());
        vo.setUpdatedAt(p.getUpdatedAt());
        return vo;
    }
}

