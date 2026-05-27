package com.yang.lblogserver.todo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "排序请求")
public class SortRequest {
    @Schema(description = "排序项")
    private List<SortItem> items;

    public List<SortItem> getItems() { return items; }
    public void setItems(List<SortItem> items) { this.items = items; }

    @Schema(description = "排序项")
    public static class SortItem {
        @Schema(description = "ID")
        private Long id;

        @Schema(description = "排序值")
        private Integer sortOrder;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    }
}
