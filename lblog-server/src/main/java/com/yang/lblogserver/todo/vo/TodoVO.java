package com.yang.lblogserver.todo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;
import java.util.List;

@Schema(description = "代办")
public class TodoVO {
    @Schema(description = "ID")
    private Long id;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "备注")
    private String note;

    @Schema(description = "优先级: 0=低 1=中 2=高")
    private Integer priority;

    @Schema(description = "状态: 0=待办 1=已完成")
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "截止日期")
    private Date dueDate;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "标签列表")
    private List<String> tags;

    @Schema(description = "子任务")
    private List<SubItemVO> items;

    @Schema(description = "创建时间")
    private Date createdAt;

    @Schema(description = "更新时间")
    private Date updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Date getDueDate() { return dueDate; }
    public void setDueDate(Date dueDate) { this.dueDate = dueDate; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public List<SubItemVO> getItems() { return items; }
    public void setItems(List<SubItemVO> items) { this.items = items; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }

    @Schema(description = "子任务")
    public static class SubItemVO {
        @Schema(description = "ID")
        private Long id;

        @Schema(description = "标题")
        private String title;

        @Schema(description = "是否完成")
        private Boolean completed;

        @Schema(description = "排序")
        private Integer sortOrder;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public Boolean getCompleted() { return completed; }
        public void setCompleted(Boolean completed) { this.completed = completed; }
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    }
}
