package com.yang.lblogserver.ai;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 SseEmitter 线程安全问题。
 *
 * 两个线程同时 send()，验证数据是否交错。
 */
class SseEmitterThreadSafetyTest {

    @Test
    void concurrentSend_shouldNotCorruptData() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        SseEmitter emitter = new SseEmitter(0L);
        emitter.onCompletion(() -> {});
        // 手动初始化 emitter（设置 response）
        // 注意：SseEmitter 需要绑定到 response 上
        // 这里我们通过发射器的 send 方法来测试

        // 直接调 emitter.send 会报错（没绑定 response），
        // 改用自定义方案验证线程安全问题
    }

    @Test
    void simulateConcurrentWrite_threadSafetyProblem() throws Exception {
        // 用两个线程同时写同一个 ByteArrayOutputStream
        // 模拟 SseEmitter 底层无锁写入的问题
        var buf = new java.io.ByteArrayOutputStream();
        var writer = new java.io.OutputStreamWriter(buf, StandardCharsets.UTF_8);
        int count = 1000;
        var latch = new CountDownLatch(2);
        var errors = new AtomicInteger(0);

        // 线程 A：写入心跳包
        Thread threadA = new Thread(() -> {
            for (int i = 0; i < count; i++) {
                try {
                    writer.write("data:{}\n\n");
                    writer.flush();
                    Thread.yield();
                } catch (Exception e) {
                    errors.incrementAndGet();
                }
            }
            latch.countDown();
        }, "heartbeat-thread");

        // 线程 B：写入大数据包（模拟 tool-call XML）
        Thread threadB = new Thread(() -> {
            StringBuilder largePayload = new StringBuilder("data:");
            largePayload.append("{\"type\":\"tool-call\",\"arguments\":{\"xml\":\"");
            for (int i = 0; i < 100; i++) {
                largePayload.append("<mxCell id=\\\"").append(i).append("\\\" value=\\\"test\\\"/>");
            }
            largePayload.append("\"}}\n\n");

            byte[] data = largePayload.toString().getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < count; i++) {
                try {
                    writer.write(largePayload.toString());
                    writer.flush();
                    Thread.yield();
                } catch (Exception e) {
                    errors.incrementAndGet();
                }
            }
            latch.countDown();
        }, "ai-thread");

        threadA.start();
        threadB.start();
        latch.await();

        writer.flush();
        String output = buf.toString(StandardCharsets.UTF_8);

        // 检查输出是否被破坏
        String[] lines = output.split("\n");
        int corrupted = 0;
        for (String line : lines) {
            if (line.isEmpty()) continue;
            // 每一行要么是 data:{}\n\n 要么是完整的 data:{...}
            if (!line.startsWith("data:")) {
                corrupted++;
            }
        }

        System.out.println("总行数: " + lines.length);
        System.out.println("损坏行数: " + corrupted);
        System.out.println("输出片段: " + output.substring(0, Math.min(500, output.length())));

        assertTrue(corrupted > 0, "期望看到线程安全问题导致的损坏行，如果为0说明当前环境未复现");
    }
}
