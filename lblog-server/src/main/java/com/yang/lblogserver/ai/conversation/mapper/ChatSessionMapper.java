package com.yang.lblogserver.ai.conversation.mapper;

import com.yang.lblogserver.ai.conversation.domain.ChatSession;
import com.yang.lblogserver.ai.conversation.domain.ChatSessionVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatSessionMapper {

    int insert(ChatSession session);

    ChatSession selectById(@Param("id") Long id);

    List<ChatSessionVO> selectByUserAndAgent(@Param("userId") Long userId,
                                              @Param("agentType") String agentType,
                                              @Param("offset") int offset,
                                              @Param("size") int size);

    int countByUserAndAgent(@Param("userId") Long userId,
                            @Param("agentType") String agentType);

    int updateTitle(@Param("id") Long id, @Param("title") String title);

    int updateStatus(@Param("id") Long id, @Param("status") int status);
}
