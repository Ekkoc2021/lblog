package com.yang.lblogserver.ai.draw;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yang.lblogserver.draw.util.MxCellValidator;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@Component
public class DisplayDiagramTool {

    private final ObjectMapper objectMapper;

    public DisplayDiagramTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Tool(name = "display_diagram", description = "Generate draw.io XML for a diagram. Call this when user asks to draw a diagram.")
    public String execute(String xml, ToolContext ctx) {
        SseEmitter emitter = (SseEmitter) ctx.getContext().get("emitter");

        MxCellValidator validator = new MxCellValidator();
        String error = validator.validate(xml);

        String wrappedXml = xml;
        if (error != null) {
            String sanitized = validator.sanitize(xml);
            String retryError = validator.validate(sanitized);
            if (retryError == null) {
                error = null;
                wrappedXml = sanitized;
            }
        }

        wrappedXml = validator.wrapWithMxFile(wrappedXml);

        if (emitter != null) {
            try {
                String payload = objectMapper.writeValueAsString(Map.of(
                        "type", "tool-call",
                        "name", "display_diagram",
                        "arguments", Map.of("xml", wrappedXml)
                ));
                emitter.send(payload);
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }

        return error == null ? "Diagram generated successfully" : "Diagram generated with validation fixes: " + error;
    }
}
