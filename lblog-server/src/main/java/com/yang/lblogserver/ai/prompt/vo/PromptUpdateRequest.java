package com.yang.lblogserver.ai.prompt.vo;

import lombok.Data;

@Data
public class PromptUpdateRequest {
    private String content;
    private String description;
    private Integer sortOrder;
    private String operator;
}
