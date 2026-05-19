package com.yang.lblogserver.ai.memory.compression;

import org.springframework.ai.chat.messages.Message;
import java.util.List;

public interface CompressionStrategy {

    boolean shouldCompress(List<Message> messages);

    List<Message> compress(List<Message> messages);
}
