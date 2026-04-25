package com.yang.lblogserver.common;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "分页数据")
public class PageResult<T> {
    @Schema(description = "数据列表")
    private List<T> list;
    @Schema(description = "总记录数")
    private long total;
    @Schema(description = "当前页码")
    private int page;
    @Schema(description = "每页条数")
    private int pageSize;

    public static <T> PageResult<T> of(int page, int pageSize, long total, List<T> list) {
        PageResult<T> r = new PageResult<>();
        r.setPage(page);
        r.setPageSize(pageSize);
        r.setTotal(total);
        r.setList(list);
        return r;
    }

    public List<T> getList() { return list; }
    public void setList(List<T> list) { this.list = list; }
    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }
}
