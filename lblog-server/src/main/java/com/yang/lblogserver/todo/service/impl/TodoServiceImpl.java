package com.yang.lblogserver.todo.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.yang.lblogserver.common.PageResult;
import com.yang.lblogserver.todo.domain.*;
import com.yang.lblogserver.todo.mapper.*;
import com.yang.lblogserver.todo.service.TodoService;
import com.yang.lblogserver.todo.vo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TodoServiceImpl implements TodoService {

    private final TodoMapper todoMapper;
    private final TodoItemMapper todoItemMapper;
    private final TodoTagMapper todoTagMapper;

    public TodoServiceImpl(TodoMapper todoMapper, TodoItemMapper todoItemMapper, TodoTagMapper todoTagMapper) {
        this.todoMapper = todoMapper;
        this.todoItemMapper = todoItemMapper;
        this.todoTagMapper = todoTagMapper;
    }

    @Override
    public PageResult<TodoVO> listTodos(Long userId, int page, int pageSize, Integer status, String tag) {
        PageHelper.startPage(page, pageSize);
        List<Todo> todos = todoMapper.selectByUserId(userId, status, null, tag);
        PageInfo<Todo> pageInfo = new PageInfo<>(todos);
        List<TodoVO> voList = todos.stream().map(t -> toVO(t, userId)).collect(Collectors.toList());
        return PageResult.of(page, pageSize, pageInfo.getTotal(), voList);
    }

    @Override
    public TodoVO getTodo(Long userId, Long id) {
        Todo todo = todoMapper.selectById(id);
        if (todo == null || !todo.getUserId().equals(userId)) return null;
        return toVO(todo, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TodoVO createTodo(Long userId, CreateTodoRequest req) {
        Todo todo = new Todo();
        todo.setUserId(userId);
        todo.setTitle(req.getTitle());
        todo.setNote(req.getNote());
        todo.setPriority(req.getPriority() != null ? req.getPriority() : 0);
        todo.setStatus(0);
        todo.setDueDate(req.getDueDate());
        todo.setSortOrder(0);
        todoMapper.insert(todo);
        if (req.getTags() != null) {
            syncTags(userId, todo.getId(), req.getTags());
        }
        return toVO(todo, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TodoVO updateTodo(Long userId, Long id, UpdateTodoRequest req) {
        Todo todo = todoMapper.selectById(id);
        if (todo == null || !todo.getUserId().equals(userId)) return null;
        if (req.getTitle() != null) todo.setTitle(req.getTitle());
        if (req.getNote() != null) todo.setNote(req.getNote());
        if (req.getPriority() != null) todo.setPriority(req.getPriority());
        if (req.getStatus() != null) todo.setStatus(req.getStatus());
        if (req.getDueDate() != null) todo.setDueDate(req.getDueDate());
        if (req.getSortOrder() != null) todo.setSortOrder(req.getSortOrder());
        todoMapper.update(todo);
        if (req.getTags() != null) {
            syncTags(userId, id, req.getTags());
        }
        return toVO(todo, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTodo(Long userId, Long id) {
        Todo todo = todoMapper.selectById(id);
        if (todo == null || !todo.getUserId().equals(userId)) return;
        todoTagMapper.deleteRelationsByTodoId(id);
        todoItemMapper.deleteByTodoId(id);
        todoMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sortTodos(Long userId, List<SortRequest.SortItem> items) {
        List<Todo> list = new ArrayList<>();
        for (SortRequest.SortItem item : items) {
            Todo t = new Todo();
            t.setId(item.getId());
            t.setSortOrder(item.getSortOrder());
            list.add(t);
        }
        todoMapper.updateSortOrders(list);
    }

    @Override
    public List<TodoVO.SubItemVO> listItems(Long userId, Long todoId) {
        Todo todo = todoMapper.selectById(todoId);
        if (todo == null || !todo.getUserId().equals(userId)) return Collections.emptyList();
        return todoItemMapper.selectByTodoId(todoId).stream().map(i -> {
            TodoVO.SubItemVO vo = new TodoVO.SubItemVO();
            vo.setId(i.getId());
            vo.setTitle(i.getTitle());
            vo.setCompleted(i.getCompleted());
            vo.setSortOrder(i.getSortOrder());
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TodoVO.SubItemVO addItem(Long userId, Long todoId, String title) {
        Todo todo = todoMapper.selectById(todoId);
        if (todo == null || !todo.getUserId().equals(userId)) return null;
        TodoItem item = new TodoItem();
        item.setTodoId(todoId);
        item.setTitle(title);
        item.setCompleted(false);
        item.setSortOrder(0);
        todoItemMapper.insert(item);
        TodoVO.SubItemVO vo = new TodoVO.SubItemVO();
        vo.setId(item.getId());
        vo.setTitle(item.getTitle());
        vo.setCompleted(item.getCompleted());
        vo.setSortOrder(item.getSortOrder());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TodoVO.SubItemVO updateItem(Long userId, Long itemId, String title, Boolean completed) {
        TodoItem item = todoItemMapper.selectById(itemId);
        if (item == null) return null;
        Todo todo = todoMapper.selectById(item.getTodoId());
        if (todo == null || !todo.getUserId().equals(userId)) return null;
        if (title != null) item.setTitle(title);
        if (completed != null) item.setCompleted(completed);
        todoItemMapper.update(item);
        TodoVO.SubItemVO vo = new TodoVO.SubItemVO();
        vo.setId(item.getId());
        vo.setTitle(item.getTitle());
        vo.setCompleted(item.getCompleted());
        vo.setSortOrder(item.getSortOrder());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteItem(Long userId, Long itemId) {
        TodoItem item = todoItemMapper.selectById(itemId);
        if (item == null) return;
        Todo todo = todoMapper.selectById(item.getTodoId());
        if (todo == null || !todo.getUserId().equals(userId)) return;
        todoItemMapper.deleteById(itemId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sortItems(Long userId, Long todoId, List<SortRequest.SortItem> items) {
        Todo todo = todoMapper.selectById(todoId);
        if (todo == null || !todo.getUserId().equals(userId)) return;
        List<TodoItem> list = new ArrayList<>();
        for (SortRequest.SortItem item : items) {
            TodoItem ti = new TodoItem();
            ti.setId(item.getId());
            ti.setSortOrder(item.getSortOrder());
            list.add(ti);
        }
        todoItemMapper.updateSortOrders(list);
    }

    @Override
    public List<String> listTags(Long userId) {
        return todoTagMapper.selectByUserId(userId).stream()
                .map(TodoTag::getName).collect(Collectors.toList());
    }

    // --- Private helpers ---

    private TodoVO toVO(Todo todo, Long userId) {
        TodoVO vo = new TodoVO();
        vo.setId(todo.getId());
        vo.setTitle(todo.getTitle());
        vo.setNote(todo.getNote());
        vo.setPriority(todo.getPriority());
        vo.setStatus(todo.getStatus());
        vo.setDueDate(todo.getDueDate());
        vo.setSortOrder(todo.getSortOrder());
        vo.setCreatedAt(todo.getCreatedAt());
        vo.setTags(todoTagMapper.selectByTodoId(todo.getId()).stream()
                .map(TodoTag::getName).collect(Collectors.toList()));
        vo.setItems(todoItemMapper.selectByTodoId(todo.getId()).stream().map(i -> {
            TodoVO.SubItemVO s = new TodoVO.SubItemVO();
            s.setId(i.getId());
            s.setTitle(i.getTitle());
            s.setCompleted(i.getCompleted());
            s.setSortOrder(i.getSortOrder());
            return s;
        }).collect(Collectors.toList()));
        return vo;
    }

    private void syncTags(Long userId, Long todoId, List<String> tagNames) {
        todoTagMapper.deleteRelationsByTodoId(todoId);
        for (String name : tagNames) {
            if (name == null || name.isBlank()) continue;
            TodoTag tag = todoTagMapper.selectByUserIdAndName(userId, name.trim());
            if (tag == null) {
                tag = new TodoTag();
                tag.setUserId(userId);
                tag.setName(name.trim());
                todoTagMapper.insert(tag);
            }
            todoTagMapper.insertRelation(todoId, tag.getId());
        }
    }
}
