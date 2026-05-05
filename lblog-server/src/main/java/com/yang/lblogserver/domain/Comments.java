package com.yang.lblogserver.domain;

import lombok.Data;
import java.util.Date;

@Data
public class Comments {
    private Long id;
    private Long postId;
    private Long parentId;
    private Long rootId;
    private Long userId;
    private String authorName;
    private String authorAvatar;
    private Long replyToUid;
    private String replyToName;
    private String content;
    private Integer status;
    private Integer likeCount;
    private Integer replyCount;
    private String ipAddress;
    private Date createdAt;
    private Date updatedAt;
    private Date deletedAt;
    private Integer isDelelte;
}
