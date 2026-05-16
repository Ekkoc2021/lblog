package com.yang.lblogserver.ai.agent.draw;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SseEvent {
    private String type;
    private String name;
    private String content;

    public static SseEvent withType(String type, String name, String content) {
        return new SseEvent(type, name, content);
    }
}
