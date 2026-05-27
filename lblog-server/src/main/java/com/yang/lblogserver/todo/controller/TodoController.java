package com.yang.lblogserver.todo.controller;

import com.yang.lblogserver.auth.security.model.LoginUser;
import com.yang.lblogserver.common.ApiResponse;
import com.yang.lblogserver.common.PageResult;
import com.yang.lblogserver.todo.service.TodoService;
import com.yang.lblogserver.todo.vo.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "代办", description = "个人代办管理")
@Validated
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('ADMIN','AUTHOR')")
public class TodoController {

    private final TodoService todoService;

    public TodoController(TodoService todoService) {
        this.todoService = todoService;
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser user) {
            return user.getUserId();
        }
        return null;
    }

    @GetMapping("/todos")
    @Operation(summary = "获取代办列表")
    public ApiResponse<PageResult<TodoVO>> list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String tag) {
        return ApiResponse.success(todoService.listTodos(getCurrentUserId(), page, pageSize, status, tag));
    }

    @PostMapping("/todos")
    @Operation(summary = "创建代办")
    public ApiResponse<TodoVO> create(@Valid @RequestBody CreateTodoRequest req) {
        return ApiResponse.success(todoService.createTodo(getCurrentUserId(), req));
    }

    @PutMapping("/todos/{id}")
    @Operation(summary = "更新代办")
    public ApiResponse<TodoVO> update(@PathVariable Long id, @Valid @RequestBody UpdateTodoRequest req) {
        TodoVO result = todoService.updateTodo(getCurrentUserId(), id, req);
        if (result == null) return ApiResponse.error(404, "代办不存在");
        return ApiResponse.success(result);
    }

    @DeleteMapping("/todos/{id}")
    @Operation(summary = "删除代办")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        todoService.deleteTodo(getCurrentUserId(), id);
        return ApiResponse.success(null);
    }

    @PutMapping("/todos/sort")
    @Operation(summary = "批量排序")
    public ApiResponse<Void> sort(@Valid @RequestBody SortRequest req) {
        todoService.sortTodos(getCurrentUserId(), req.getItems());
        return ApiResponse.success(null);
    }

    // --- 子任务 ---

    @GetMapping("/todos/{id}/items")
    @Operation(summary = "获取子任务列表")
    public ApiResponse<List<TodoVO.SubItemVO>> listItems(@PathVariable Long id) {
        return ApiResponse.success(todoService.listItems(getCurrentUserId(), id));
    }

    @PostMapping("/todos/{id}/items")
    @Operation(summary = "添加子任务")
    public ApiResponse<TodoVO.SubItemVO> addItem(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String title = body.get("title");
        return ApiResponse.success(todoService.addItem(getCurrentUserId(), id, title));
    }

    @PutMapping("/todos/items/{id}")
    @Operation(summary = "更新子任务")
    public ApiResponse<TodoVO.SubItemVO> updateItem(@PathVariable Long id,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Boolean completed) {
        return ApiResponse.success(todoService.updateItem(getCurrentUserId(), id, title, completed));
    }

    @DeleteMapping("/todos/items/{id}")
    @Operation(summary = "删除子任务")
    public ApiResponse<Void> deleteItem(@PathVariable Long id) {
        todoService.deleteItem(getCurrentUserId(), id);
        return ApiResponse.success(null);
    }

    @PutMapping("/todos/{id}/items/sort")
    @Operation(summary = "子任务排序")
    public ApiResponse<Void> sortItems(@PathVariable Long id, @Valid @RequestBody SortRequest req) {
        todoService.sortItems(getCurrentUserId(), id, req.getItems());
        return ApiResponse.success(null);
    }

    // --- 标签 ---

    @GetMapping("/todo-tags")
    @Operation(summary = "获取用户标签列表")
    public ApiResponse<List<String>> listTags() {
        return ApiResponse.success(todoService.listTags(getCurrentUserId()));
    }
}
