package com.yang.lblogserver.todo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.util.Date;
import java.util.List;

@Schema(description = "更新代办请求")
public class UpdateTodoRequest {
    @Size(max = 500)
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

    @Schema(description = "标签名列表（全量替换）")
    private List<String> tags;

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
}
