package com.example.barcodefloating;

public class ScanItem {
    private final String content;
    private final String format;
    private final long timestamp;

    public ScanItem(String content, String format, long timestamp) {
        this.content = content;
        this.format = format;
        this.timestamp = timestamp;
    }

    public String getContent() {
        return content;
    }

    public String getFormat() {
        return format != null ? format : "BARCODE";
    }

    public long getTimestamp() {
        return timestamp;
    }
}
