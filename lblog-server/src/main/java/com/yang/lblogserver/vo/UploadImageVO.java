package com.yang.lblogserver.vo;

public class UploadImageVO {
    private String url;
    private String filename;
    private long size;
    private String mimeType;

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
}
