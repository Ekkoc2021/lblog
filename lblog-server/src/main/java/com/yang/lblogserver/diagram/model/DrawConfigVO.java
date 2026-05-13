package com.yang.lblogserver.diagram.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DrawConfigVO {
    private boolean enabled;
    private String model;
    private int maxTokens;
}
