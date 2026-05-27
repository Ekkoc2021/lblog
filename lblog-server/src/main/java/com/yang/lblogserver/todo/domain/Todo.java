package com.yang.lblogserver.todo.domain;

import lombok.Data;
import java.util.Date;

@Data
public class Todo {
    private Long id;
    private Long userId;
    private String title;
    private String note;
    private Integer priority;
    private Integer status;
    private Date dueDate;
    private Integer sortOrder;
    private Date createdAt;
    private Date updatedAt;
}
