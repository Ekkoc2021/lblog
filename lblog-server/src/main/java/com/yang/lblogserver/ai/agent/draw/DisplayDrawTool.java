package com.yang.lblogserver.ai.agent.draw;

import com.yang.lblogserver.draw.util.MxCellValidator;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@Component
public class DisplayDrawTool {

    private final MxCellValidator validator;

    public DisplayDrawTool(MxCellValidator validator) {
        this.validator = validator;
    }

    @Tool(name = "display_diagram", description = "Generate draw.io XML for a diagram. Call this when user asks to draw a diagram.")
    public String execute(String xml, ToolContext ctx) {
        SseEmitter emitter = (SseEmitter) ctx.getContext().get("emitter");

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
                emitter.send(SseEmitter.event()
                        .data(Map.of("type", "tool-call", "name", "display_diagram",
                                "arguments", Map.of("xml", wrappedXml))));
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }

        return error == null ? "Diagram generated successfully" : "Diagram generated with validation fixes: " + error;
    }
}
