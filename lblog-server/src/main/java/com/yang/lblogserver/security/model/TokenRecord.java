package com.yang.lblogserver.security.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TokenRecord {
    private Long id;
    private Long userId;
    private String tokenHash;
    private String tokenType;    // ACCESS / REFRESH
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private boolean revoked;
    private String replacedBy;
}
