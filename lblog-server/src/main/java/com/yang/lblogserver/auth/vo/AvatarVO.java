package com.yang.lblogserver.auth.vo;

public class AvatarVO {

    private Long id;
    private String url;

    public AvatarVO(Long id, String url) {
        this.id = id;
        this.url = url;
    }

    public Long getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }
}
