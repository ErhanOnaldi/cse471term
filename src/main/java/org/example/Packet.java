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
                if (pt.code == c) return pt;
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
    }

    public Packet(PacketType type, int ttl, String sourceIP) {
        this();
        this.type = type;
        this.seqNumber = getNextSeqNumber();
        this.ttl = ttl;
        this.sourceIP = (sourceIP != null) ? sourceIP : "";
    }

    // Getter setter
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

    public byte[] toBytes() {
        byte[] sourceIPBytes = sourceIP.getBytes(StandardCharsets.UTF_8);
        int sourceIpLen = sourceIPBytes.length;

        byte[] fileHashBytes = fileHash.getBytes(StandardCharsets.UTF_8);
        int fileHashLen = fileHashBytes.length;

        int chunkDataLen = (chunkData != null) ? chunkData.length : 0;

        byte[] msgBytes = message.getBytes(StandardCharsets.UTF_8);
        int msgLen = msgBytes.length;

        int totalSize = 16
                + 8 // fileSize
                + 4 + sourceIpLen
                + 4 + fileHashLen
                + 4 + chunkDataLen
                + 4 + msgLen;

        ByteBuffer buf = ByteBuffer.allocate(totalSize);
        buf.putInt(type.getCode());
        buf.putInt(seqNumber);
        buf.putInt(ttl);
        buf.putInt(chunkIndex);
        buf.putLong(fileSize);

        buf.putInt(sourceIpLen);
        buf.put(sourceIPBytes);

        buf.putInt(fileHashLen);
        buf.put(fileHashBytes);

        buf.putInt(chunkDataLen);
        if (chunkDataLen > 0) {
            buf.put(chunkData);
        }

        buf.putInt(msgLen);
        buf.put(msgBytes);

        return buf.array();
    }

    public static Packet fromBytes(byte[] data) {
        Packet pkt = new Packet();
        try {
            ByteBuffer buf = ByteBuffer.wrap(data);

            int tcode = buf.getInt();
            pkt.type = PacketType.fromCode(tcode);

            pkt.seqNumber = buf.getInt();
            pkt.ttl = buf.getInt();
            pkt.chunkIndex = buf.getInt();
            pkt.fileSize = buf.getLong();

            int sourceIpLen = buf.getInt();
            if (sourceIpLen > 0) {
                byte[] sip = new byte[sourceIpLen];
                buf.get(sip);
                pkt.sourceIP = new String(sip, StandardCharsets.UTF_8);
            }

            int fileHashLen = buf.getInt();
            if (fileHashLen > 0) {
                byte[] fh = new byte[fileHashLen];
                buf.get(fh);
                pkt.fileHash = new String(fh, StandardCharsets.UTF_8);
            }

            int chunkDataLen = buf.getInt();
            if (chunkDataLen > 0) {
                byte[] cd = new byte[chunkDataLen];
                buf.get(cd);
                pkt.chunkData = cd;
            }

            int msgLen = buf.getInt();
            if (msgLen > 0) {
                byte[] msgb = new byte[msgLen];
                buf.get(msgb);
                pkt.message = new String(msgb, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return pkt;
    }
}
