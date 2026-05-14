package com.yang.lblogserver.diagram.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yang.lblogserver.diagram.util.MxCellValidator;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * AI 可调用的 display_diagram 工具。
 *
 * 这是 Spring AI 的 @Tool 注解方法，被 AI 通过 function calling 调用。
 * AI 输出 {"xml": "<draw.io XML>"} → Spring AI 反序列化 → 执行 execute(xml)
 *
 * 关键设计：
 *   1. 不直接处理 XML，而是通过 SSE 将 XML 推给前端渲染
 *   2. 前端收到 tool-call 事件后调用 drawioRef.load(xml) 渲染
 *   3. 返回值仅作为占位符回传给 AI，AI 据此继续对话
 *
 * ThreadLocal 模式：
 *   DiagramService.chatStream() 在入口处绑定 emitter，
 *   工具方法在任意线程都可获取并推送 SSE 事件。
 *   请求结束后必须解绑，否则会造成内存泄漏。
 */
@Component
public class DisplayDiagramTool {

    // ThreadLocal 存储当前请求的 SseEmitter
    // 为什么不用参数注入：Spring AI 的 @Tool 方法只接收 AI 输出中的参数（xml），
    // 无法注入额外的 Spring Bean 或请求上下文。ThreadLocal 是绕开此限制的常用方案。
    private static final ThreadLocal<SseEmitter> EMITTER_HOLDER = new ThreadLocal<>();

    private final ObjectMapper objectMapper;

    public DisplayDiagramTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 在入口（DiagramService.chatStream()）调用，绑定当前请求的 emitter。
     */
    public static void bindEmitter(SseEmitter emitter) {
        EMITTER_HOLDER.set(emitter);
    }

    /**
     * 请求结束时调用，清除 ThreadLocal 防止内存泄漏。
     */
    public static void unbindEmitter() {
        EMITTER_HOLDER.remove();
    }

    /**
     * @Tool 方法，被 AI 调用。
     *
     * AI 输出格式：{xml: "<draw.io XML>"}
     * Spring AI 自动将 AI 输出的 JSON 反序列化为方法参数 xml（String）。
     *
     * 执行步骤：
     *   1. 从 ThreadLocal 获取当前请求的 SseEmitter
     *   2. 校验 AI 生成的 XML 是否合法
     *   3. 如果校验不通过，尝试自动修复（sanitize）
     *   4. 将裸 mxCell 包装成完整的 mxfile 结构
     *   5. 通过 SSE 推送 tool-call 事件 → 前端渲染
     *   6. 返回占位符字符串 → Spring AI 回传给 AI
     *
     * @param xml AI 生成的 draw.io 裸 XML（只含 mxCell 元素）
     * @return 占位符结果（Spring AI 会自动回传给 AI 作为 tool result）
     */
    @Tool(name = "display_diagram", description = "Generate draw.io XML for a diagram. Call this when user asks to draw a diagram.")
    public String execute(String xml) {
        // 从 ThreadLocal 取当前请求的 SSE 连接
        SseEmitter emitter = EMITTER_HOLDER.get();

        // 校验 AI 生成的 XML 结构
        MxCellValidator validator = new MxCellValidator();
        String error = validator.validate(xml);

        // 如果校验失败，尝试自动修复
        String wrappedXml = xml;
        if (error != null) {
            String sanitized = validator.sanitize(xml);
            String retryError = validator.validate(sanitized);
            if (retryError == null) {
                // 修复后校验通过
                error = null;
                wrappedXml = sanitized;
            }
        }

        // 包装成完整的 mxfile 结构（前端 draw.io 需要）
        wrappedXml = validator.wrapWithMxFile(wrappedXml);

        // 通过 SSE 推送给前端
        if (emitter != null) {
            try {
                // tool-call 事件结构：与前端 SseEvent 类型对应
                String payload = objectMapper.writeValueAsString(Map.of(
                        "type", "tool-call",           // 事件类型
                        "name", "display_diagram",     // 工具名称
                        "arguments", Map.of("xml", wrappedXml) // XML 参数
                ));
                emitter.send(payload);
            } catch (Exception e) {
                // 推送失败（前端断连等），结束 SSE 连接
                emitter.completeWithError(e);
            }
        }

        // 返回值会由 Spring AI 自动回传给 AI 作为 tool call result
        // AI 拿到这个值后继续生成文字回复
        return error == null ? "Diagram generated successfully" : "Diagram generated with validation fixes: " + error;
    }
}
