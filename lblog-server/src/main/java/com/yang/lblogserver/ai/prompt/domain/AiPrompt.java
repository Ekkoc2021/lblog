package com.yang.lblogserver.ai.prompt.domain;

import lombok.Data;

import java.util.Date;

@Data
public class AiPrompt {
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
}
