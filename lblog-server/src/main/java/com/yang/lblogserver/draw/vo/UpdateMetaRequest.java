package com.yang.lblogserver.draw.vo;

import lombok.Data;

@Data
public class UpdateMetaRequest {
    private String title;
    private String description;
    private String tags;
}
