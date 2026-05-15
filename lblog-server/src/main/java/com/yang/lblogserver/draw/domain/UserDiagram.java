package com.yang.lblogserver.draw.domain;

import lombok.Data;
import java.util.Date;

@Data
public class UserDiagram {
    private Long id;
    private Long userId;
    private String title;
    private String description;
    private String tags;
    private String xmlData;
    private String thumbnail;
    private Integer fileSize;
    private Date createdAt;
    private Date updatedAt;
    private Date deletedAt;
}
