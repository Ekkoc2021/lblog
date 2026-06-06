package com.yang.lblogserver.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "批量操作结果")
public class BatchOpResult {

    @Schema(description = "受影响数量")
    private long count;

    public BatchOpResult(long count) { this.count = count; }

    public long getCount() { return count; }
}
