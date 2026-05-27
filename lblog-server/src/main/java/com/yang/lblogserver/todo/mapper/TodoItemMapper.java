package com.yang.lblogserver.todo.mapper;

import com.yang.lblogserver.todo.domain.TodoItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface TodoItemMapper {
    List<TodoItem> selectByTodoId(@Param("todoId") Long todoId);
    List<TodoItem> selectByTodoIds(@Param("todoIds") List<Long> todoIds);
    TodoItem selectById(@Param("id") Long id);
    int insert(TodoItem item);
    int update(TodoItem item);
    int deleteById(@Param("id") Long id);
    int deleteByTodoId(@Param("todoId") Long todoId);
    int updateSortOrders(@Param("items") List<TodoItem> items);
}
