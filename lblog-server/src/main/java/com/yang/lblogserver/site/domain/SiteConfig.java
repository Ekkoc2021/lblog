package com.yang.lblogserver.site.domain;

import lombok.Data;
import java.util.Date;

@Data
public class SiteConfig {
    private Long id;
    private String configKey;
    private String configValue;
    private Date createdAt;
    private Date updatedAt;
}
