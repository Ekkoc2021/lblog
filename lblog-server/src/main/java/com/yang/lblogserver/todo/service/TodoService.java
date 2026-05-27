package com.yang.lblogserver.todo.service;

import com.yang.lblogserver.common.PageResult;
import com.yang.lblogserver.todo.vo.*;
import java.util.List;

public interface TodoService {
    PageResult<TodoVO> listTodos(Long userId, int page, int pageSize, Integer status, Integer priority, String tag);
    TodoVO getTodo(Long userId, Long id);
    TodoVO createTodo(Long userId, CreateTodoRequest req);
    TodoVO updateTodo(Long userId, Long id, UpdateTodoRequest req);
    void deleteTodo(Long userId, Long id);
    void sortTodos(Long userId, List<SortRequest.SortItem> items);

    List<TodoVO.SubItemVO> listItems(Long userId, Long todoId);
    TodoVO.SubItemVO addItem(Long userId, Long todoId, String title);
    TodoVO.SubItemVO updateItem(Long userId, Long itemId, String title, Boolean completed);
    void deleteItem(Long userId, Long itemId);
    void sortItems(Long userId, Long todoId, List<SortRequest.SortItem> items);

    List<String> listTags(Long userId);
}
