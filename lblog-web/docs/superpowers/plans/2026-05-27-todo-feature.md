# 代办功能 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在博客工具箱中添加浮动代办面板，支持优先级、截止日期、标签、子任务和拖拽排序。

**Architecture:** 后端 Spring Boot + MyBatis 新增 todo 模块，前端 React + Ant Design 新增可拖拽 TodoPanel 浮动面板组件，复用现有 `request<T>()` API 层和 `DrawFloatingButton` 工具箱入口。

**Tech Stack:** Spring Boot 3.5.7, MyBatis, PageHelper (后端) / React 19, Ant Design 6, TypeScript (前端)

**Important:** 本项目 CLAUDE.md 约束：后端文件写入需先获得用户确认。后端任务仅为参考，实施时需逐项确认。

---

## Phase 1: 后端 (lblog-server) — 参考，需逐项确认后实施

> 以下任务涉及 `lblog-server/` 目录写入操作。根据项目 CLAUDE.md，**每项任务实施前必须先获得用户确认**。

### Task 1: 数据库 DDL

**Files:**
- Create: `lblog-server/src/main/resources/sql/todo_v1.sql` (或直接执行)

```sql
CREATE TABLE todos (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT NOT NULL,
    title         VARCHAR(500) NOT NULL,
    note          TEXT,
    priority      TINYINT DEFAULT 0,
    status        TINYINT DEFAULT 0,
    due_date      DATE,
    sort_order    INT DEFAULT 0,
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_todos_user (user_id)
);

CREATE TABLE todo_tags (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT NOT NULL,
    name          VARCHAR(50) NOT NULL,
    UNIQUE KEY uk_user_tag (user_id, name)
);

CREATE TABLE todo_tag_relations (
    todo_id       BIGINT NOT NULL,
    tag_id        BIGINT NOT NULL,
    PRIMARY KEY (todo_id, tag_id),
    FOREIGN KEY (todo_id) REFERENCES todos(id) ON DELETE CASCADE,
    FOREIGN KEY (tag_id) REFERENCES todo_tags(id) ON DELETE CASCADE
);

CREATE TABLE todo_items (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    todo_id       BIGINT NOT NULL,
    title         VARCHAR(500) NOT NULL,
    completed     TINYINT(1) DEFAULT 0,
    sort_order    INT DEFAULT 0,
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (todo_id) REFERENCES todos(id) ON DELETE CASCADE
);
```

### Task 2: Domain 实体

**Files:**
- Create: `lblog-server/src/main/java/com/yang/lblogserver/todo/domain/Todo.java`
- Create: `lblog-server/src/main/java/com/yang/lblogserver/todo/domain/TodoItem.java`
- Create: `lblog-server/src/main/java/com/yang/lblogserver/todo/domain/TodoTag.java`

```java
// Todo.java
package com.yang.lblogserver.todo.domain;

import lombok.Data;
import java.util.Date;

@Data
public class Todo {
    private Long id;
    private Long userId;
    private String title;
    private String note;
    private Integer priority;
    private Integer status;
    private Date dueDate;
    private Integer sortOrder;
    private Date createdAt;
    private Date updatedAt;
}
```

```java
// TodoItem.java
package com.yang.lblogserver.todo.domain;

import lombok.Data;
import java.util.Date;

@Data
public class TodoItem {
    private Long id;
    private Long todoId;
    private String title;
    private Boolean completed;
    private Integer sortOrder;
    private Date createdAt;
}
```

```java
// TodoTag.java
package com.yang.lblogserver.todo.domain;

import lombok.Data;

@Data
public class TodoTag {
    private Long id;
    private Long userId;
    private String name;
}
```

### Task 3: Mapper 接口 + XML

**Files:**
- Create: `lblog-server/src/main/java/com/yang/lblogserver/todo/mapper/TodoMapper.java`
- Create: `lblog-server/src/main/java/com/yang/lblogserver/todo/mapper/TodoItemMapper.java`
- Create: `lblog-server/src/main/java/com/yang/lblogserver/todo/mapper/TodoTagMapper.java`
- Create: `lblog-server/src/main/resources/com/yang/lblogserver/todo/mapper/TodoMapper.xml`
- Create: `lblog-server/src/main/resources/com/yang/lblogserver/todo/mapper/TodoItemMapper.xml`
- Create: `lblog-server/src/main/resources/com/yang/lblogserver/todo/mapper/TodoTagMapper.xml`

```java
// TodoMapper.java
package com.yang.lblogserver.todo.mapper;

import com.yang.lblogserver.todo.domain.Todo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface TodoMapper {
    List<Todo> selectByUserId(@Param("userId") Long userId,
                              @Param("status") Integer status,
                              @Param("priority") Integer priority,
                              @Param("tag") String tag);

    Todo selectById(@Param("id") Long id);

    int insert(Todo todo);

    int update(Todo todo);

    int deleteById(@Param("id") Long id);

    int updateSortOrders(@Param("items") List<Todo> items);
}
```

```java
// TodoItemMapper.java
package com.yang.lblogserver.todo.mapper;

import com.yang.lblogserver.todo.domain.TodoItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface TodoItemMapper {
    List<TodoItem> selectByTodoId(@Param("todoId") Long todoId);

    int insert(TodoItem item);

    int update(TodoItem item);

    int deleteById(@Param("id") Long id);

    int deleteByTodoId(@Param("todoId") Long todoId);

    int updateSortOrders(@Param("items") List<TodoItem> items);
}
```

```java
// TodoTagMapper.java
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
```

```xml
<!-- TodoMapper.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
    "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.yang.lblogserver.todo.mapper.TodoMapper">

    <resultMap id="TodoResult" type="com.yang.lblogserver.todo.domain.Todo">
        <id property="id" column="id"/>
        <result property="userId" column="user_id"/>
        <result property="title" column="title"/>
        <result property="note" column="note"/>
        <result property="priority" column="priority"/>
        <result property="status" column="status"/>
        <result property="dueDate" column="due_date"/>
        <result property="sortOrder" column="sort_order"/>
        <result property="createdAt" column="created_at"/>
        <result property="updatedAt" column="updated_at"/>
    </resultMap>

    <select id="selectByUserId" resultMap="TodoResult">
        SELECT DISTINCT t.* FROM todos t
        <if test="tag != null and tag != ''">
            INNER JOIN todo_tag_relations r ON t.id = r.todo_id
            INNER JOIN todo_tags g ON r.tag_id = g.id AND g.name = #{tag}
        </if>
        WHERE t.user_id = #{userId}
        <if test="status != null">AND t.status = #{status}</if>
        <if test="priority != null">AND t.priority = #{priority}</if>
        ORDER BY t.sort_order ASC, t.created_at DESC
    </select>

    <select id="selectById" resultMap="TodoResult">
        SELECT * FROM todos WHERE id = #{id}
    </select>

    <insert id="insert" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO todos (user_id, title, note, priority, status, due_date, sort_order)
        VALUES (#{userId}, #{title}, #{note}, #{priority}, #{status}, #{dueDate}, #{sortOrder})
    </insert>

    <update id="update">
        UPDATE todos
        <set>
            <if test="title != null">title = #{title},</if>
            <if test="note != null">note = #{note},</if>
            <if test="priority != null">priority = #{priority},</if>
            <if test="status != null">status = #{status},</if>
            <if test="dueDate != null">due_date = #{dueDate},</if>
            <if test="sortOrder != null">sort_order = #{sortOrder},</if>
        </set>
        WHERE id = #{id}
    </update>

    <delete id="deleteById">
        DELETE FROM todos WHERE id = #{id}
    </delete>

    <update id="updateSortOrders">
        <foreach collection="items" item="item" separator=";">
            UPDATE todos SET sort_order = #{item.sortOrder} WHERE id = #{item.id}
        </foreach>
    </update>
</mapper>
```

```xml
<!-- TodoItemMapper.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
    "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.yang.lblogserver.todo.mapper.TodoItemMapper">

    <resultMap id="TodoItemResult" type="com.yang.lblogserver.todo.domain.TodoItem">
        <id property="id" column="id"/>
        <result property="todoId" column="todo_id"/>
        <result property="title" column="title"/>
        <result property="completed" column="completed"/>
        <result property="sortOrder" column="sort_order"/>
        <result property="createdAt" column="created_at"/>
    </resultMap>

    <select id="selectByTodoId" resultMap="TodoItemResult">
        SELECT * FROM todo_items WHERE todo_id = #{todoId} ORDER BY sort_order ASC
    </select>

    <insert id="insert" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO todo_items (todo_id, title, completed, sort_order)
        VALUES (#{todoId}, #{title}, #{completed}, #{sortOrder})
    </insert>

    <update id="update">
        UPDATE todo_items
        <set>
            <if test="title != null">title = #{title},</if>
            <if test="completed != null">completed = #{completed},</if>
            <if test="sortOrder != null">sort_order = #{sortOrder},</if>
        </set>
        WHERE id = #{id}
    </update>

    <delete id="deleteById">
        DELETE FROM todo_items WHERE id = #{id}
    </delete>

    <delete id="deleteByTodoId">
        DELETE FROM todo_items WHERE todo_id = #{todoId}
    </delete>

    <update id="updateSortOrders">
        <foreach collection="items" item="item" separator=";">
            UPDATE todo_items SET sort_order = #{item.sortOrder} WHERE id = #{item.id}
        </foreach>
    </update>
</mapper>
```

```xml
<!-- TodoTagMapper.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
    "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.yang.lblogserver.todo.mapper.TodoTagMapper">

    <resultMap id="TodoTagResult" type="com.yang.lblogserver.todo.domain.TodoTag">
        <id property="id" column="id"/>
        <result property="userId" column="user_id"/>
        <result property="name" column="name"/>
    </resultMap>

    <select id="selectByUserId" resultMap="TodoTagResult">
        SELECT * FROM todo_tags WHERE user_id = #{userId} ORDER BY name
    </select>

    <select id="selectByUserIdAndName" resultMap="TodoTagResult">
        SELECT * FROM todo_tags WHERE user_id = #{userId} AND name = #{name}
    </select>

    <insert id="insert" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO todo_tags (user_id, name) VALUES (#{userId}, #{name})
    </insert>

    <select id="selectByTodoId" resultMap="TodoTagResult">
        SELECT g.* FROM todo_tags g
        INNER JOIN todo_tag_relations r ON g.id = r.tag_id
        WHERE r.todo_id = #{todoId}
    </select>

    <insert id="insertRelation">
        INSERT INTO todo_tag_relations (todo_id, tag_id) VALUES (#{todoId}, #{tagId})
    </insert>

    <delete id="deleteRelationsByTodoId">
        DELETE FROM todo_tag_relations WHERE todo_id = #{todoId}
    </delete>
</mapper>
```

### Task 4: VO / DTO

**Files:**
- Create: `lblog-server/src/main/java/com/yang/lblogserver/todo/vo/TodoVO.java`
- Create: `lblog-server/src/main/java/com/yang/lblogserver/todo/vo/CreateTodoRequest.java`
- Create: `lblog-server/src/main/java/com/yang/lblogserver/todo/vo/UpdateTodoRequest.java`
- Create: `lblog-server/src/main/java/com/yang/lblogserver/todo/vo/SortRequest.java`

```java
// TodoVO.java
package com.yang.lblogserver.todo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;
import java.util.List;

@Schema(description = "代办")
public class TodoVO {
    @Schema(description = "ID")
    public Long id;

    @Schema(description = "标题")
    public String title;

    @Schema(description = "备注")
    public String note;

    @Schema(description = "优先级: 0=低 1=中 2=高")
    public Integer priority;

    @Schema(description = "状态: 0=待办 1=已完成")
    public Integer status;

    @Schema(description = "截止日期")
    public Date dueDate;

    @Schema(description = "排序")
    public Integer sortOrder;

    @Schema(description = "标签列表")
    public List<String> tags;

    @Schema(description = "子任务")
    public List<SubItemVO> items;

    @Schema(description = "创建时间")
    public Date createdAt;

    @Schema(description = "子任务")
    public static class SubItemVO {
        @Schema(description = "ID")
        public Long id;

        @Schema(description = "标题")
        public String title;

        @Schema(description = "是否完成")
        public Boolean completed;

        @Schema(description = "排序")
        public Integer sortOrder;
    }
}
```

```java
// CreateTodoRequest.java
package com.yang.lblogserver.todo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Date;
import java.util.List;

@Schema(description = "创建代办请求")
public class CreateTodoRequest {
    @NotBlank
    @Size(max = 500)
    @Schema(description = "标题", required = true)
    public String title;

    @Schema(description = "备注")
    public String note;

    @Schema(description = "优先级: 0=低 1=中 2=高")
    public Integer priority;

    @Schema(description = "截止日期")
    public Date dueDate;

    @Schema(description = "标签名列表")
    public List<String> tags;
}
```

```java
// UpdateTodoRequest.java
package com.yang.lblogserver.todo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.util.Date;
import java.util.List;

@Schema(description = "更新代办请求")
public class UpdateTodoRequest {
    @Size(max = 500)
    @Schema(description = "标题")
    public String title;

    @Schema(description = "备注")
    public String note;

    @Schema(description = "优先级: 0=低 1=中 2=高")
    public Integer priority;

    @Schema(description = "状态: 0=待办 1=已完成")
    public Integer status;

    @Schema(description = "截止日期")
    public Date dueDate;

    @Schema(description = "排序")
    public Integer sortOrder;

    @Schema(description = "标签名列表（全量替换）")
    public List<String> tags;
}
```

```java
// SortRequest.java
package com.yang.lblogserver.todo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "排序请求")
public class SortRequest {
    @Schema(description = "排序项")
    public List<SortItem> items;

    @Schema(description = "排序项")
    public static class SortItem {
        @Schema(description = "ID")
        public Long id;

        @Schema(description = "排序值")
        public Integer sortOrder;
    }
}
```

### Task 5: Service 接口 + 实现

**Files:**
- Create: `lblog-server/src/main/java/com/yang/lblogserver/todo/service/TodoService.java`
- Create: `lblog-server/src/main/java/com/yang/lblogserver/todo/service/impl/TodoServiceImpl.java`

```java
// TodoService.java
package com.yang.lblogserver.todo.service;

import com.yang.lblogserver.common.PageResult;
import com.yang.lblogserver.todo.vo.*;
import java.util.List;

public interface TodoService {
    PageResult<TodoVO> listTodos(Long userId, int page, int pageSize, Integer status, String tag);
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
```

```java
// TodoServiceImpl.java
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
        todo.setTitle(req.title);
        todo.setNote(req.note);
        todo.setPriority(req.priority != null ? req.priority : 0);
        todo.setStatus(0);
        todo.setDueDate(req.dueDate);
        todo.setSortOrder(0);
        todoMapper.insert(todo);
        if (req.tags != null) {
            syncTags(userId, todo.getId(), req.tags);
        }
        return toVO(todo, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TodoVO updateTodo(Long userId, Long id, UpdateTodoRequest req) {
        Todo todo = todoMapper.selectById(id);
        if (todo == null || !todo.getUserId().equals(userId)) return null;
        if (req.title != null) todo.setTitle(req.title);
        if (req.note != null) todo.setNote(req.note);
        if (req.priority != null) todo.setPriority(req.priority);
        if (req.status != null) todo.setStatus(req.status);
        if (req.dueDate != null) todo.setDueDate(req.dueDate);
        if (req.sortOrder != null) todo.setSortOrder(req.sortOrder);
        todoMapper.update(todo);
        if (req.tags != null) {
            syncTags(userId, id, req.tags);
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
            t.setId(item.id);
            t.setSortOrder(item.sortOrder);
            list.add(t);
        }
        todoMapper.updateSortOrders(list);
    }

    // --- 子任务 ---

    @Override
    public List<TodoVO.SubItemVO> listItems(Long userId, Long todoId) {
        Todo todo = todoMapper.selectById(todoId);
        if (todo == null || !todo.getUserId().equals(userId)) return Collections.emptyList();
        return todoItemMapper.selectByTodoId(todoId).stream().map(i -> {
            TodoVO.SubItemVO vo = new TodoVO.SubItemVO();
            vo.id = i.getId();
            vo.title = i.getTitle();
            vo.completed = i.getCompleted();
            vo.sortOrder = i.getSortOrder();
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
        vo.id = item.getId();
        vo.title = item.getTitle();
        vo.completed = item.getCompleted();
        vo.sortOrder = item.getSortOrder();
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TodoVO.SubItemVO updateItem(Long userId, Long itemId, String title, Boolean completed) {
        TodoItem item = todoItemMapper.selectById(itemId); // need to add this method
        if (item == null) return null;
        // Verify ownership via parent todo
        Todo todo = todoMapper.selectById(item.getTodoId());
        if (todo == null || !todo.getUserId().equals(userId)) return null;
        if (title != null) item.setTitle(title);
        if (completed != null) item.setCompleted(completed);
        todoItemMapper.update(item);
        TodoVO.SubItemVO vo = new TodoVO.SubItemVO();
        vo.id = item.getId();
        vo.title = item.getTitle();
        vo.completed = item.getCompleted();
        vo.sortOrder = item.getSortOrder();
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
            ti.setId(item.id);
            ti.setSortOrder(item.sortOrder);
            list.add(ti);
        }
        todoItemMapper.updateSortOrders(list);
    }

    // --- 标签 ---

    @Override
    public List<String> listTags(Long userId) {
        return todoTagMapper.selectByUserId(userId).stream()
                .map(TodoTag::getName).collect(Collectors.toList());
    }

    // --- 内部方法 ---

    private TodoVO toVO(Todo todo, Long userId) {
        TodoVO vo = new TodoVO();
        vo.id = todo.getId();
        vo.title = todo.getTitle();
        vo.note = todo.getNote();
        vo.priority = todo.getPriority();
        vo.status = todo.getStatus();
        vo.dueDate = todo.getDueDate();
        vo.sortOrder = todo.getSortOrder();
        vo.createdAt = todo.getCreatedAt();
        vo.tags = todoTagMapper.selectByTodoId(todo.getId()).stream()
                .map(TodoTag::getName).collect(Collectors.toList());
        vo.items = todoItemMapper.selectByTodoId(todo.getId()).stream().map(i -> {
            TodoVO.SubItemVO s = new TodoVO.SubItemVO();
            s.id = i.getId();
            s.title = i.getTitle();
            s.completed = i.getCompleted();
            s.sortOrder = i.getSortOrder();
            return s;
        }).collect(Collectors.toList());
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
```

> **注意:** `TodoItemMapper` 需要额外添加 `selectById` 方法，Service 中使用了它。

### Task 6: Controller

**Files:**
- Create: `lblog-server/src/main/java/com/yang/lblogserver/todo/controller/TodoController.java`

```java
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
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
        todoService.sortTodos(getCurrentUserId(), req.items);
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
    public ApiResponse<TodoVO.SubItemVO> addItem(@PathVariable Long id,
            @RequestBody @NotBlank String title) { // simplified: wrap in Map or custom type
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
        todoService.sortItems(getCurrentUserId(), id, req.items);
        return ApiResponse.success(null);
    }

    // --- 标签 ---

    @GetMapping("/todo-tags")
    @Operation(summary = "获取用户标签列表")
    public ApiResponse<List<String>> listTags() {
        return ApiResponse.success(todoService.listTags(getCurrentUserId()));
    }
}
```

> **注意:** 子任务添加接口的 `@RequestBody @NotBlank String title` 接收单个字符串在 Spring 中有坑，建议改为 `@RequestBody Map<String, String> body` 取 `body.get("title")`，或新增一个简单的 `AddItemRequest` 类。

---

## Phase 2: 前端 (lblog-web) — 实施

### Task 7: 类型定义

**Files:**
- Modify: `src/types/index.ts`

在文件末尾追加以下类型：

```typescript
// ---- 代办 ----

export interface TodoItem {
  id: number;
  title: string;
  completed: boolean;
  sortOrder: number;
}

export interface Todo {
  id: number;
  title: string;
  note: string | null;
  priority: number;       // 0=低 1=中 2=高
  status: number;         // 0=待办 1=已完成
  dueDate: string | null;
  sortOrder: number;
  tags: string[];
  items: TodoItem[];
  createdAt: string;
}

export interface CreateTodoRequest {
  title: string;
  note?: string;
  priority?: number;
  dueDate?: string;
  tags?: string[];
}

export interface UpdateTodoRequest {
  title?: string;
  note?: string;
  priority?: number;
  status?: number;
  dueDate?: string;
  sortOrder?: number;
  tags?: string[];
}

export interface SortRequest {
  items: { id: number; sortOrder: number }[];
}
```

### Task 8: API 服务层

**Files:**
- Create: `src/services/todoApi.ts`

```typescript
import type { ApiResponse, PageResult, Todo, CreateTodoRequest, UpdateTodoRequest, SortRequest } from '../types';
import { request, buildQuery } from './api';

export function getTodos(params?: {
  page?: number;
  pageSize?: number;
  status?: number;
  tag?: string;
}): Promise<ApiResponse<PageResult<Todo>>> {
  return request<PageResult<Todo>>(`/api/v1/todos${buildQuery(params as Record<string, string | number | undefined>)}`);
}

export function createTodo(data: CreateTodoRequest): Promise<ApiResponse<Todo>> {
  return request<Todo>('/api/v1/todos', {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

export function updateTodo(id: number, data: UpdateTodoRequest): Promise<ApiResponse<Todo>> {
  return request<Todo>(`/api/v1/todos/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

export function deleteTodo(id: number): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/todos/${id}`, { method: 'DELETE' });
}

export function sortTodos(data: SortRequest): Promise<ApiResponse<null>> {
  return request<null>('/api/v1/todos/sort', {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

export function addTodoItem(todoId: number, title: string): Promise<ApiResponse<import('../types').TodoItem>> {
  return request<import('../types').TodoItem>(`/api/v1/todos/${todoId}/items`, {
    method: 'POST',
    body: JSON.stringify({ title }),
  });
}

export function updateTodoItem(itemId: number, data: { title?: string; completed?: boolean }): Promise<ApiResponse<import('../types').TodoItem>> {
  return request<import('../types').TodoItem>(`/api/v1/todos/items/${itemId}?${buildQuery(data as Record<string, string | number | undefined>)}`, {
    method: 'PUT',
  });
}

export function deleteTodoItem(itemId: number): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/todos/items/${itemId}`, { method: 'DELETE' });
}

export function sortTodoItems(todoId: number, data: SortRequest): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/todos/${todoId}/items/sort`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

export function getTodoTags(): Promise<ApiResponse<string[]>> {
  return request<string[]>('/api/v1/todo-tags');
}
```

### Task 9: useTodos Hook

**Files:**
- Create: `src/hooks/useTodos.ts`

```typescript
import { useState, useCallback } from 'react';
import { message } from 'antd';
import type { Todo, CreateTodoRequest, UpdateTodoRequest } from '../types';
import * as todoApi from '../services/todoApi';

export function useTodos() {
  const [todos, setTodos] = useState<Todo[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [tags, setTags] = useState<string[]>([]);

  const loadTodos = useCallback(async (params?: { page?: number; pageSize?: number; status?: number; tag?: string }) => {
    setLoading(true);
    try {
      const res = await todoApi.getTodos({ pageSize: 100, ...params });
      setTodos(res.data.list);
      setTotal(res.data.total);
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    } finally {
      setLoading(false);
    }
  }, []);

  const loadTags = useCallback(async () => {
    try {
      const res = await todoApi.getTodoTags();
      setTags(res.data);
    } catch { /* tags are non-critical */ }
  }, []);

  const create = useCallback(async (data: CreateTodoRequest) => {
    try {
      await todoApi.createTodo(data);
      message.success('代办已创建');
      await loadTodos();
      await loadTags();
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    }
  }, [loadTodos, loadTags]);

  const update = useCallback(async (id: number, data: UpdateTodoRequest) => {
    try {
      await todoApi.updateTodo(id, data);
      await loadTodos();
      await loadTags();
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    }
  }, [loadTodos, loadTags]);

  const remove = useCallback(async (id: number) => {
    try {
      await todoApi.deleteTodo(id);
      message.success('代办已删除');
      await loadTodos();
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    }
  }, [loadTodos]);

  const addItem = useCallback(async (todoId: number, title: string) => {
    try {
      await todoApi.addTodoItem(todoId, title);
      await loadTodos();
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    }
  }, [loadTodos]);

  const updateItem = useCallback(async (itemId: number, data: { title?: string; completed?: boolean }) => {
    try {
      await todoApi.updateTodoItem(itemId, data);
      await loadTodos();
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    }
  }, [loadTodos]);

  const removeItem = useCallback(async (itemId: number) => {
    try {
      await todoApi.deleteTodoItem(itemId);
      await loadTodos();
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    }
  }, [loadTodos]);

  const reorder = useCallback(async (items: { id: number; sortOrder: number }[]) => {
    try {
      await todoApi.sortTodos({ items });
      await loadTodos();
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    }
  }, [loadTodos]);

  return { todos, loading, total, tags, loadTodos, loadTags, create, update, remove, addItem, updateItem, removeItem, reorder };
}
```

### Task 10: TodoSubtaskList 组件

**Files:**
- Create: `src/components/TodoSubtaskList.tsx`

```tsx
import { useState } from 'react';
import { Input, Checkbox } from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import type { TodoItem } from '../types';

interface Props {
  todoId: number;
  items: TodoItem[];
  onAdd: (todoId: number, title: string) => Promise<void>;
  onUpdate: (itemId: number, data: { title?: string; completed?: boolean }) => Promise<void>;
  onDelete: (itemId: number) => Promise<void>;
}

const TodoSubtaskList: React.FC<Props> = ({ todoId, items, onAdd, onUpdate, onDelete }) => {
  const [newTitle, setNewTitle] = useState('');
  const [adding, setAdding] = useState(false);

  const handleAdd = async () => {
    const t = newTitle.trim();
    if (!t) return;
    setAdding(true);
    await onAdd(todoId, t);
    setNewTitle('');
    setAdding(false);
  };

  return (
    <div style={{ paddingLeft: 20, marginTop: 6 }}>
      {items.map(item => (
        <div key={item.id} style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '2px 0' }}>
          <Checkbox
            checked={item.completed}
            onChange={e => onUpdate(item.id, { completed: e.target.checked })}
          />
          <span style={{
            flex: 1,
            fontSize: 13,
            textDecoration: item.completed ? 'line-through' : 'none',
            color: item.completed ? '#999' : undefined,
          }}>
            {item.title}
          </span>
          <DeleteOutlined
            style={{ fontSize: 12, color: '#bbb', cursor: 'pointer', flexShrink: 0 }}
            onClick={() => onDelete(item.id)}
          />
        </div>
      ))}
      <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginTop: 4 }}>
        <PlusOutlined style={{ fontSize: 12, color: '#bbb' }} />
        <Input
          size="small"
          variant="borderless"
          placeholder="添加子任务"
          value={newTitle}
          onChange={e => setNewTitle(e.target.value)}
          onPressEnter={handleAdd}
          disabled={adding}
          style={{ fontSize: 13, padding: 0, height: 24 }}
        />
      </div>
    </div>
  );
};

export default TodoSubtaskList;
```

### Task 11: TodoItem 组件

**Files:**
- Create: `src/components/TodoItem.tsx`

```tsx
import { useState } from 'react';
import { Checkbox, Tag, Popconfirm } from 'antd';
import { DeleteOutlined, DownOutlined, RightOutlined } from '@ant-design/icons';
import type { Todo } from '../types';
import TodoSubtaskList from './TodoSubtaskList';

interface Props {
  todo: Todo;
  onUpdate: (id: number, data: { status?: number; title?: string; priority?: number; dueDate?: string; tags?: string[]; note?: string }) => Promise<void>;
  onDelete: (id: number) => Promise<void>;
  onAddItem: (todoId: number, title: string) => Promise<void>;
  onUpdateItem: (itemId: number, data: { title?: string; completed?: boolean }) => Promise<void>;
  onDeleteItem: (itemId: number) => Promise<void>;
  dragHandleProps?: Record<string, unknown>;
}

const PRIORITY_MAP: Record<number, { label: string; color: string }> = {
  0: { label: '低', color: '#52c41a' },
  1: { label: '中', color: '#faad14' },
  2: { label: '高', color: '#ff4d4f' },
};

function isOverdue(dueDate: string | null): boolean {
  if (!dueDate) return false;
  return new Date(dueDate) < new Date(new Date().toDateString());
}

const TodoItem: React.FC<Props> = ({ todo, onUpdate, onDelete, onAddItem, onUpdateItem, onDeleteItem, dragHandleProps }) => {
  const [expanded, setExpanded] = useState(false);
  const hasItems = todo.items.length > 0;
  const p = PRIORITY_MAP[todo.priority] ?? PRIORITY_MAP[0];

  return (
    <div
      style={{
        padding: '8px 14px',
        borderBottom: '1px solid #f0f0f0',
        background: todo.status === 1 ? '#fafafa' : undefined,
      }}
    >
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 8 }}>
        {/* Drag handle */}
        <span
          {...dragHandleProps}
          style={{ cursor: 'grab', color: '#ccc', fontSize: 14, lineHeight: '22px', userSelect: 'none', flexShrink: 0 }}
        >
          ⠿
        </span>

        {/* Checkbox */}
        <Checkbox
          checked={todo.status === 1}
          onChange={e => onUpdate(todo.id, { status: e.target.checked ? 1 : 0 })}
          style={{ flexShrink: 0 }}
        />

        {/* Content */}
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
            <span style={{
              textDecoration: todo.status === 1 ? 'line-through' : 'none',
              color: todo.status === 1 ? '#999' : undefined,
              fontSize: 14,
              wordBreak: 'break-word',
            }}>
              {todo.title}
            </span>
            <Tag color={p.color} style={{ margin: 0, fontSize: 11, lineHeight: '18px' }}>{p.label}</Tag>
            {isOverdue(todo.dueDate) && todo.status === 0 && (
              <Tag color="red" style={{ margin: 0, fontSize: 11, lineHeight: '18px' }}>已过期</Tag>
            )}
          </div>

          {/* Meta row */}
          <div style={{ fontSize: 12, color: '#999', marginTop: 2, display: 'flex', gap: 12, flexWrap: 'wrap' }}>
            {todo.dueDate && <span>📅 {todo.dueDate}</span>}
            {todo.tags.map(t => (
              <Tag key={t} style={{ margin: 0, fontSize: 11, lineHeight: '18px' }}>{t}</Tag>
            ))}
            {hasItems && (
              <span
                style={{ cursor: 'pointer', color: '#1677ff' }}
                onClick={() => setExpanded(!expanded)}
              >
                {expanded ? <DownOutlined /> : <RightOutlined />} {todo.items.length} 子任务
                ({todo.items.filter(i => i.completed).length}/{todo.items.length})
              </span>
            )}
            {!hasItems && (
              <span
                style={{ cursor: 'pointer', color: '#bbb' }}
                onClick={() => setExpanded(!expanded)}
              >
                {expanded ? <DownOutlined /> : <RightOutlined />} 子任务
              </span>
            )}
          </div>

          {/* Subtasks */}
          {expanded && (
            <TodoSubtaskList
              todoId={todo.id}
              items={todo.items}
              onAdd={onAddItem}
              onUpdate={onUpdateItem}
              onDelete={onDeleteItem}
            />
          )}
        </div>

        {/* Delete */}
        <Popconfirm
          title="确定删除？"
          onConfirm={() => onDelete(todo.id)}
          okText="删除"
          cancelText="取消"
        >
          <DeleteOutlined style={{ color: '#bbb', cursor: 'pointer', flexShrink: 0, marginTop: 2 }} />
        </Popconfirm>
      </div>
    </div>
  );
};

export default TodoItem;
```

### Task 12: TodoList 组件

**Files:**
- Create: `src/components/TodoList.tsx`

```tsx
import { useCallback } from 'react';
import { Spin, Empty } from 'antd';
import type { Todo } from '../types';
import TodoItem from './TodoItem';

interface Props {
  todos: Todo[];
  loading: boolean;
  onUpdate: (id: number, data: { status?: number }) => Promise<void>;
  onDelete: (id: number) => Promise<void>;
  onAddItem: (todoId: number, title: string) => Promise<void>;
  onUpdateItem: (itemId: number, data: { title?: string; completed?: boolean }) => Promise<void>;
  onDeleteItem: (itemId: number) => Promise<void>;
  onReorder: (items: { id: number; sortOrder: number }[]) => Promise<void>;
}

const TodoList: React.FC<Props> = ({ todos, loading, onUpdate, onDelete, onAddItem, onUpdateItem, onDeleteItem, onReorder }) => {
  const handleDragStart = useCallback((e: React.DragEvent, index: number) => {
    e.dataTransfer.setData('text/plain', String(index));
    (e.currentTarget as HTMLElement).style.opacity = '0.5';
  }, []);

  const handleDragEnd = useCallback((e: React.DragEvent) => {
    (e.currentTarget as HTMLElement).style.opacity = '1';
  }, []);

  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
  }, []);

  const handleDrop = useCallback((e: React.DragEvent, dropIndex: number) => {
    e.preventDefault();
    const dragIndex = parseInt(e.dataTransfer.getData('text/plain'), 10);
    if (dragIndex === dropIndex) return;

    const reordered = [...todos];
    const [moved] = reordered.splice(dragIndex, 1);
    reordered.splice(dropIndex, 0, moved);

    const items = reordered.map((t, i) => ({ id: t.id, sortOrder: i }));
    onReorder(items);
  }, [todos, onReorder]);

  if (loading) return <Spin style={{ display: 'block', padding: 40, textAlign: 'center' }} />;
  if (todos.length === 0) return <Empty description="暂无代办" image={Empty.PRESENTED_IMAGE_SIMPLE} style={{ padding: 30 }} />;

  return (
    <div style={{ maxHeight: '50vh', overflowY: 'auto' }}>
      {todos.map((todo, index) => (
        <div
          key={todo.id}
          draggable
          onDragStart={e => handleDragStart(e, index)}
          onDragEnd={handleDragEnd}
          onDragOver={handleDragOver}
          onDrop={e => handleDrop(e, index)}
        >
          <TodoItem
            todo={todo}
            onUpdate={onUpdate}
            onDelete={onDelete}
            onAddItem={onAddItem}
            onUpdateItem={onUpdateItem}
            onDeleteItem={onDeleteItem}
            dragHandleProps={{}}
          />
        </div>
      ))}
    </div>
  );
};

export default TodoList;
```

### Task 13: TodoPanel 组件

**Files:**
- Create: `src/components/TodoPanel.tsx`

```tsx
import { useState, useEffect, useRef, useCallback } from 'react';
import { Tabs, Button, Modal, Form, Input, Select, DatePicker, message } from 'antd';
import { PlusOutlined, CloseOutlined, CheckSquareOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useTodos } from '../hooks/useTodos';
import TodoList from './TodoList';
import type { CreateTodoRequest } from '../types';

const PRIORITY_OPTIONS = [
  { value: 0, label: '低' },
  { value: 1, label: '中' },
  { value: 2, label: '高' },
];

interface Props {
  onClose: () => void;
}

const TodoPanel: React.FC<Props> = ({ onClose }) => {
  const { todos, loading, total, tags, loadTodos, loadTags, create, update, remove, addItem, updateItem, removeItem, reorder } = useTodos();
  const [pos, setPos] = useState({ left: window.innerWidth - 420, top: 80 });
  const [tab, setTab] = useState('all');
  const [formVisible, setFormVisible] = useState(false);
  const [form] = Form.useForm();
  const dragging = useRef(false);
  const dragStart = useRef({ x: 0, y: 0, left: 0, top: 0 });

  useEffect(() => { loadTodos(); loadTags(); }, [loadTodos, loadTags]);

  const handleMouseDown = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    dragging.current = true;
    dragStart.current = { x: e.clientX, y: e.clientY, left: pos.left, top: pos.top };
    const onMove = (ev: MouseEvent) => {
      if (!dragging.current) return;
      setPos({
        left: Math.max(0, Math.min(window.innerWidth - 400, dragStart.current.left + ev.clientX - dragStart.current.x)),
        top: Math.max(0, Math.min(window.innerHeight - 100, dragStart.current.top + ev.clientY - dragStart.current.y)),
      });
    };
    const onUp = () => { dragging.current = false; window.removeEventListener('mousemove', onMove); window.removeEventListener('mouseup', onUp); };
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
  }, [pos]);

  const handleCreate = async () => {
    try {
      const values = await form.validateFields();
      const data: CreateTodoRequest = {
        title: values.title,
        note: values.note,
        priority: values.priority ?? 0,
        dueDate: values.dueDate ? dayjs(values.dueDate).format('YYYY-MM-DD') : undefined,
        tags: values.tags ?? [],
      };
      await create(data);
      form.resetFields();
      setFormVisible(false);
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    }
  };

  const handleUpdate = async (id: number, data: { status?: number }) => {
    await update(id, data);
  };

  const filteredTodos = todos.filter(t => {
    if (tab === 'active') return t.status === 0;
    if (tab === 'done') return t.status === 1;
    return true;
  });

  return (
    <div style={{
      position: 'fixed',
      left: pos.left,
      top: pos.top,
      width: 380,
      maxHeight: '70vh',
      zIndex: 1000,
      background: '#fff',
      borderRadius: 12,
      boxShadow: '0 8px 40px rgba(0,0,0,0.12)',
      display: 'flex',
      flexDirection: 'column',
      overflow: 'hidden',
    }}>
      {/* Header — draggable */}
      <div
        onMouseDown={handleMouseDown}
        style={{
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          padding: '10px 16px', cursor: 'grab', userSelect: 'none',
          borderBottom: '1px solid #f0f0f0', flexShrink: 0,
        }}
      >
        <span style={{ fontWeight: 600, fontSize: 15 }}>
          <CheckSquareOutlined style={{ marginRight: 8 }} />
          我的代办
          <span style={{ marginLeft: 8, fontSize: 12, color: '#999', fontWeight: 400 }}>
            {todos.filter(t => t.status === 0).length}/{total}
          </span>
        </span>
        <CloseOutlined style={{ cursor: 'pointer', color: '#999' }} onClick={onClose} />
      </div>

      {/* Tabs + Add */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 16px', borderBottom: '1px solid #f0f0f0', flexShrink: 0 }}>
        <Tabs
          activeKey={tab}
          onChange={setTab}
          size="small"
          style={{ marginBottom: 0 }}
          items={[
            { key: 'all', label: `全部 (${total})` },
            { key: 'active', label: `进行中 (${todos.filter(t => t.status === 0).length})` },
            { key: 'done', label: `已完成 (${todos.filter(t => t.status === 1).length})` },
          ]}
        />
        <Button type="primary" size="small" icon={<PlusOutlined />} onClick={() => setFormVisible(true)}>新建</Button>
      </div>

      {/* List */}
      <div style={{ flex: 1, overflow: 'auto' }}>
        <TodoList
          todos={filteredTodos}
          loading={loading}
          onUpdate={handleUpdate}
          onDelete={remove}
          onAddItem={addItem}
          onUpdateItem={updateItem}
          onDeleteItem={removeItem}
          onReorder={reorder}
        />
      </div>

      {/* Create Modal */}
      <Modal
        title="新建代办"
        open={formVisible}
        onOk={handleCreate}
        onCancel={() => setFormVisible(false)}
        okText="创建"
        cancelText="取消"
        destroyOnClose
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="title" label="标题" rules={[{ required: true, message: '请输入标题' }]}>
            <Input placeholder="要做什么？" maxLength={500} />
          </Form.Item>
          <Form.Item name="note" label="备注">
            <Input.TextArea placeholder="详细描述（可选）" rows={2} />
          </Form.Item>
          <Form.Item name="priority" label="优先级">
            <Select options={PRIORITY_OPTIONS} placeholder="选择优先级" />
          </Form.Item>
          <Form.Item name="dueDate" label="截止日期">
            <DatePicker style={{ width: '100%' }} placeholder="选择日期（可选）" />
          </Form.Item>
          <Form.Item name="tags" label="标签">
            <Select mode="tags" placeholder="输入标签，回车添加" options={tags.map(t => ({ value: t, label: t }))} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default TodoPanel;
```

### Task 14: 工具箱集成 — DrawFloatingButton

**Files:**
- Modify: `src/components/DrawFloatingButton.tsx`

在 `tools` 数组中新增代办条目。修改两处：

1. 顶部 import 添加图标：
```tsx
import { Wrench, PencilRuler, CheckSquare } from 'lucide-react'
```

2. `tools` 数组追加：
```tsx
const tools: ToolItem[] = [
  { id: 'draw', label: '绘图工具', icon: <PencilRuler size={14} />, action: () => { setHover(false); onOpenDraw() } },
  { id: 'todo', label: '代办事项', icon: <CheckSquare size={14} />, action: () => { setHover(false); props.onOpenTodo() } },
]
```

3. Props 接口新增回调：
```tsx
interface DrawFloatingButtonProps {
  onOpenDraw: () => void
  onOpenTodo: () => void
  onPositionChange?: (pos: { left: number; top: number }) => void
  hidden?: boolean
}
```

### Task 15: App.tsx 集成

**Files:**
- Modify: `src/App.tsx`

1. 顶部 import:
```tsx
import TodoPanel from './components/TodoPanel';
```

2. 在 `AppContent` 组件内新增状态和渲染:
```tsx
const [showTodoPanel, setShowTodoPanel] = useState(false);
```

3. 修改 `ToolboxButton` JSX（在 `DrawFloatingButton` 处添加 `onOpenTodo`）:
```tsx
<ToolboxButton
  showDrawPage={showDrawPage}
  onOpenDraw={() => setShowDrawPage(true)}
  onOpenTodo={() => setShowTodoPanel(v => !v)}
  onPositionChange={setToolboxPos}
/>
```

需要同步修改 `ToolboxButton` 包装组件的 props 和内部调用。

4. 在 JSX 中添加 TodoPanel 渲染（与 DrawPage 的 div 同级，放在最后即可）:
```tsx
{showTodoPanel && <TodoPanel onClose={() => setShowTodoPanel(false)} />}
```

---

## Task Summary

| Phase | Task | 描述 | 文件操作 |
|-------|------|------|----------|
| 1 | 1-6 | 后端 (DB + Domain + Mapper + VO + Service + Controller) | 新建 ~16 个文件 (需用户确认) |
| 2 | 7 | 类型定义 | 修改 `src/types/index.ts` |
| 2 | 8 | API 服务层 | 新建 `src/services/todoApi.ts` |
| 2 | 9 | useTodos Hook | 新建 `src/hooks/useTodos.ts` |
| 2 | 10 | TodoSubtaskList | 新建 `src/components/TodoSubtaskList.tsx` |
| 2 | 11 | TodoItem | 新建 `src/components/TodoItem.tsx` |
| 2 | 12 | TodoList | 新建 `src/components/TodoList.tsx` |
| 2 | 13 | TodoPanel | 新建 `src/components/TodoPanel.tsx` |
| 2 | 14 | 工具箱集成 | 修改 `src/components/DrawFloatingButton.tsx` |
| 2 | 15 | App.tsx 集成 | 修改 `src/App.tsx` |

---

*Generated by writing-plans skill*
