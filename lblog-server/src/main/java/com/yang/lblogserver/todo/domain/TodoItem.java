package com.yang.lblogserver.todo.domain;

import lombok.Data;
import java.util.Date;

@Data
public class TodoItem {
    private Long id;
    private Long todoId;
    private String title;
    private Boolean completed;
    private Integer sortOrder;
    private Date createdAt;
}
