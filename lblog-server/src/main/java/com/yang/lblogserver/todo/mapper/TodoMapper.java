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
