package org.example;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public class Packet {

    public enum PacketType {
        DISCOVERY(1),
        DISCOVERY_RESPONSE(2),
        SEARCH(3),
        SEARCH_RESPONSE(4),
        CHUNK_REQUEST(5),
        CHUNK_RESPONSE(6),
        OTHER(99);

        private final int code;
        PacketType(int c) { this.code = c; }
        public int getCode() { return code; }
        public static PacketType fromCode(int c) {
            for (PacketType pt : values()) {
                if (pt.code == c) {
                    return pt;
                }
            }
            return OTHER;
        }
    }

    private static final AtomicInteger GLOBAL_SEQ = new AtomicInteger(0);
    public static int getNextSeqNumber() {
        return GLOBAL_SEQ.incrementAndGet();
    }

    private PacketType type;
    private int seqNumber;
    private int ttl;
    private String sourceIP;
    private String fileHash;
    private int chunkIndex;
    private byte[] chunkData;
    private String message;

    private long fileSize;

    // *** YENİ ALAN ***
    private String nodeId; // Hangi node'un paketi?

    public Packet() {
        this.type = PacketType.OTHER;
        this.seqNumber = 0;
        this.ttl = 0;
        this.sourceIP = "";
        this.fileHash = "";
        this.chunkIndex = -1;
        this.chunkData = null;
        this.message = "";
        this.fileSize = 0;
        this.nodeId = "";
    }

    public Packet(PacketType type, int ttl, String sourceIP) {
        this();
        this.type = type;
        this.seqNumber = getNextSeqNumber();
        this.ttl = ttl;
        this.sourceIP = (sourceIP != null) ? sourceIP : "";
    }

    // Getter / Setter
    public PacketType getType() { return type; }
    public void setType(PacketType t) { this.type = t; }
    public int getSeqNumber() { return seqNumber; }
    public void setSeqNumber(int s) { this.seqNumber = s; }
    public int getTtl() { return ttl; }
    public void setTtl(int t) { this.ttl = t; }
    public String getSourceIP() { return sourceIP; }
    public void setSourceIP(String s) { this.sourceIP = s; }
    public String getFileHash() { return fileHash; }
    public void setFileHash(String fh) { this.fileHash = fh; }
    public int getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(int ci) { this.chunkIndex = ci; }
    public byte[] getChunkData() { return chunkData; }
    public void setChunkData(byte[] cd) { this.chunkData = cd; }
    public String getMessage() { return message; }
    public void setMessage(String m) { this.message = m; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fs) { this.fileSize = fs; }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public byte[] toBytes() {
        byte[] sourceIPBytes = sourceIP.getBytes(StandardCharsets.UTF_8);
        int sourceIpLen = sourceIPBytes.length;

        byte[] fileHashBytes = fileHash.getBytes(StandardCharsets.UTF_8);
        int fileHashLen = fileHashBytes.length;

        int chunkDataLen = (chunkData != null) ? chunkData.length : 0;

        byte[] msgBytes = message.getBytes(StandardCharsets.UTF_8);
        int msgLen = msgBytes.length;

        byte[] nodeIdBytes = (nodeId != null) ? nodeId.getBytes(StandardCharsets.UTF_8) : new byte[0];
        int nodeIdLen = nodeIdBytes.length;

        int totalSize = 16
                + 8
                + 4 + sourceIpLen
                + 4 + fileHashLen
                + 4 + chunkDataLen
                + 4 + msgLen
                + 4 + nodeIdLen;

        ByteBuffer buffer = ByteBuffer.allocate(totalSize);

        buffer.putInt(type.getCode());
        buffer.putInt(seqNumber);
        buffer.putInt(ttl);
        buffer.putInt(chunkIndex);
        buffer.putLong(fileSize);

        buffer.putInt(sourceIpLen);
        buffer.put(sourceIPBytes);

        buffer.putInt(fileHashLen);
        buffer.put(fileHashBytes);

        buffer.putInt(chunkDataLen);
        if (chunkDataLen > 0) {
            buffer.put(chunkData);
        }

        buffer.putInt(msgLen);
        buffer.put(msgBytes);


        buffer.putInt(nodeIdLen);
        if (nodeIdLen > 0) {
            buffer.put(nodeIdBytes);
        }

        return buffer.array();
    }

    public static Packet fromBytes(byte[] data) {
        Packet pkt = new Packet();
        try {
            ByteBuffer buffer = ByteBuffer.wrap(data);

            int typeCode = buffer.getInt();
            pkt.type = PacketType.fromCode(typeCode);

            pkt.seqNumber = buffer.getInt();
            pkt.ttl = buffer.getInt();
            pkt.chunkIndex = buffer.getInt();
            pkt.fileSize = buffer.getLong();

            int sourceIpLen = buffer.getInt();
            if (sourceIpLen > 0) {
                byte[] sip = new byte[sourceIpLen];
                buffer.get(sip);
                pkt.sourceIP = new String(sip, StandardCharsets.UTF_8);
            } else {
                pkt.sourceIP = "";
            }

            int fileHashLen = buffer.getInt();
            if (fileHashLen > 0) {
                byte[] fh = new byte[fileHashLen];
                buffer.get(fh);
                pkt.fileHash = new String(fh, StandardCharsets.UTF_8);
            } else {
                pkt.fileHash = "";
            }

            int chunkDataLen = buffer.getInt();
            if (chunkDataLen > 0) {
                byte[] cdata = new byte[chunkDataLen];
                buffer.get(cdata);
                pkt.chunkData = cdata;
            } else {
                pkt.chunkData = null;
            }

            int msgLen = buffer.getInt();
            if (msgLen > 0) {
                byte[] msgb = new byte[msgLen];
                buffer.get(msgb);
                pkt.message = new String(msgb, StandardCharsets.UTF_8);
            } else {
                pkt.message = "";
            }

            int nodeIdLen = buffer.getInt();
            if (nodeIdLen > 0) {
                byte[] nid = new byte[nodeIdLen];
                buffer.get(nid);
                pkt.nodeId = new String(nid, StandardCharsets.UTF_8);
            } else {
                pkt.nodeId = "";
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return pkt;
    }
}
