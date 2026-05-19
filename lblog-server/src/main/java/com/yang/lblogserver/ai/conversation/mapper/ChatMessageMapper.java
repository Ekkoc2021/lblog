package com.yang.lblogserver.ai.conversation.mapper;

import com.yang.lblogserver.ai.conversation.domain.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatMessageMapper {

    int insert(ChatMessage message);

    int batchInsert(@Param("list") List<ChatMessage> messages);

    List<ChatMessage> selectBySessionId(@Param("sessionId") Long sessionId);

    List<ChatMessage> selectRecentBySessionId(@Param("sessionId") Long sessionId,
                                              @Param("limit") int limit);

    int selectMaxMsgIndex(@Param("sessionId") Long sessionId);

    int deleteBySessionId(@Param("sessionId") Long sessionId);
}
