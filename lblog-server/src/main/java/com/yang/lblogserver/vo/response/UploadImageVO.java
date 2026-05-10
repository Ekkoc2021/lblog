package com.yang.lblogserver.vo.response;

public class UploadImageVO {
    private String url;
    private String filename;
    private long size;
    private String mimeType;
    private Long imageId;

    public UploadImageVO(String url, String filename, long size, String mimeType) {
        this.url = url;
        this.filename = filename;
        this.size = size;
        this.mimeType = mimeType;
    }

    public String getUrl() { return url; }
    public String getFilename() { return filename; }
    public long getSize() { return size; }
    public String getMimeType() { return mimeType; }
    public Long getImageId() { return imageId; }
    public void setImageId(Long imageId) { this.imageId = imageId; }
}
