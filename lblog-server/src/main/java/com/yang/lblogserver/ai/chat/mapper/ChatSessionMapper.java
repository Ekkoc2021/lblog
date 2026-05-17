package com.yang.lblogserver.ai.chat.mapper;

import com.yang.lblogserver.ai.chat.domain.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatSessionMapper {

    int insert(ChatSession session);

    ChatSession selectById(@Param("id") Long id);

    List<ChatSession> selectByUserAndAgent(@Param("userId") Long userId,
                                           @Param("agentType") String agentType,
                                           @Param("offset") int offset,
                                           @Param("size") int size);

    int countByUserAndAgent(@Param("userId") Long userId,
                            @Param("agentType") String agentType);

    int updateTitle(@Param("id") Long id, @Param("title") String title);

    int updateStats(@Param("sessionId") Long sessionId,
                    @Param("delta") int delta,
                    @Param("tokens") int tokens);

    int updateStatus(@Param("id") Long id, @Param("status") int status);
}
