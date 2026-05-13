package com.yang.lblogserver.diagram.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yang.lblogserver.diagram.util.MxCellValidator;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@Component
public class DisplayDiagramTool {

    private static final ThreadLocal<SseEmitter> EMITTER_HOLDER = new ThreadLocal<>();

    private final ObjectMapper objectMapper;

    public DisplayDiagramTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public static void bindEmitter(SseEmitter emitter) {
        EMITTER_HOLDER.set(emitter);
    }

    public static void unbindEmitter() {
        EMITTER_HOLDER.remove();
    }

    @Tool(name = "display_diagram", description = "Generate draw.io XML for a diagram. Call this when user asks to draw a diagram.")
    public String execute(String xml) {
        SseEmitter emitter = EMITTER_HOLDER.get();

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
