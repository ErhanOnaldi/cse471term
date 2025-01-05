package org.example;

public class PeerInfo {
    private String ipAddress;
    private long fileSize;

    public PeerInfo(String ip, long size) {
        this.ipAddress = ip;
        this.fileSize = size;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public long getFileSize() {
        return fileSize;
    }
}
