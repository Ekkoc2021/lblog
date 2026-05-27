package com.yang.lblogserver.todo.mapper;

import com.yang.lblogserver.todo.domain.TodoTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface TodoTagMapper {
    List<TodoTag> selectByUserId(@Param("userId") Long userId);
    TodoTag selectByUserIdAndName(@Param("userId") Long userId, @Param("name") String name);
    int insert(TodoTag tag);
    List<TodoTag> selectByTodoId(@Param("todoId") Long todoId);
    int insertRelation(@Param("todoId") Long todoId, @Param("tagId") Long tagId);
    int deleteRelationsByTodoId(@Param("todoId") Long todoId);
}
