package com.yang.lblogserver.ai.prompt.domain;

import lombok.Data;

import java.util.Date;

@Data
public class AiPromptAudit {
    private Long id;
    private Long promptId;
    private String module;
    private String promptKey;
    private String oldContent;
    private String newContent;
    private Integer oldVersion;
    private Integer newVersion;
    private String action;
    private String operator;
    private String remark;
    private Date createdAt;
}
