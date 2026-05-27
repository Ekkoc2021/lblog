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
    private String title;

    @Schema(description = "备注")
    private String note;

    @Schema(description = "优先级: 0=低 1=中 2=高")
    private Integer priority;

    @Schema(description = "截止日期")
    private Date dueDate;

    @Schema(description = "标签名列表")
    private List<String> tags;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public Date getDueDate() { return dueDate; }
    public void setDueDate(Date dueDate) { this.dueDate = dueDate; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
}
