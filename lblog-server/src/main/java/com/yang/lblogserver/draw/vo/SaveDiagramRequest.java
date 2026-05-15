package com.yang.lblogserver.draw.vo;

import lombok.Data;

@Data
public class SaveDiagramRequest {
    private String title;
    private String description;
    private String tags;
    private String xmlData;
    private String thumbnail;
    private Integer fileSize;
}
