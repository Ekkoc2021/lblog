package com.yang.lblogserver.todo.domain;

import lombok.Data;

@Data
public class TodoTag {
    private Long id;
    private Long userId;
    private String name;
}
