package com.yang.lblogserver.ai.agent.draw;

import com.yang.lblogserver.draw.util.MxCellValidator;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import com.yang.lblogserver.ai.agent.transport.AgentStreamTransport;

import java.util.Map;

@Component
public class DisplayDrawTool {

    private final MxCellValidator validator;

    public DisplayDrawTool(MxCellValidator validator) {
        this.validator = validator;
    }

    @Tool(name = "display_diagram", description = "Generate draw.io XML for a diagram. Call this when user asks to draw a diagram.")
    public String execute(String xml, ToolContext ctx) {
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

        AgentStreamTransport transport = (AgentStreamTransport) ctx.getContext().get("transport");
        if (transport != null) {
            transport.send("tool-call", Map.of("name", "display_diagram", "arguments", Map.of("xml", wrappedXml)));
        }

        return error == null ? "Diagram generated successfully" : "Diagram generated with validation fixes: " + error;
    }
}
