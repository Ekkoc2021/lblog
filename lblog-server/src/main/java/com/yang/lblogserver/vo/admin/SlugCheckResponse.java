package com.yang.lblogserver.vo.admin;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Slug可用性检查响应")
public class SlugCheckResponse {
    @Schema(description = "是否可用")
    private boolean available;

    public SlugCheckResponse() {}

    public SlugCheckResponse(boolean available) {
        this.available = available;
    }

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
}
