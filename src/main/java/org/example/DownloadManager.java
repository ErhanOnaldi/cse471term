package org.example;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class DownloadManager {

    private final P2PNode node;
    private final String fileHash;
    private final long fileSize;
    private final File destinationFolder;

    private int totalChunks;
    private byte[][] chunkBuffers;
    private int chunksReceived;
    private boolean isDownloading;

    private String sourceIP = "127.0.0.1"; // basit senaryoda sabit

    public DownloadManager(P2PNode node, String fileHash, long fileSize, File destFolder) {
        this.node = node;
        this.fileHash = fileHash;
        this.fileSize = fileSize;
        this.destinationFolder = destFolder;

        final int CHUNK_SIZE = 256 * 1024;
        this.totalChunks = (int)Math.ceil((double)fileSize / CHUNK_SIZE);
        if (this.totalChunks <= 0) this.totalChunks = 1;

        this.chunkBuffers = new byte[totalChunks][];
        this.isDownloading = false;
        this.chunksReceived = 0;
    }

    public void startDownload() {
        isDownloading = true;
        System.out.println("[DownloadManager] Start download hash=" + fileHash
                + ", size=" + fileSize + ", totalChunks=" + totalChunks);

        // Sırasıyla bütün chunk'ları iste
        for (int i = 0; i < totalChunks; i++) {
            node.requestChunk(sourceIP, fileHash, i);
            try {
                Thread.sleep(50); // Yüksek trafikten kaçınmak için minik bekleme
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // "finalizeDownload" chunksReceived == totalChunks olduğunda çağrılacak
    }

    public synchronized void handleChunkData(int index, byte[] data) {
        if (!isDownloading) return;
        if (index < 0 || index >= totalChunks) return;

        chunkBuffers[index] = data;
        chunksReceived++;

        double percent = (chunksReceived * 100.0) / totalChunks;
        System.out.printf("[DownloadManager] chunk %d/%d (%.2f%%)\n",
                chunksReceived, totalChunks, percent);

        if (chunksReceived == totalChunks) {
            finalizeDownload();
        }
    }

    private void finalizeDownload() {
        isDownloading = false;

        File outFile = new File(destinationFolder, fileHash + "_downloaded.dat");
        System.out.println("[DownloadManager] Writing to: " + outFile.getAbsolutePath());
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            for (byte[] cdata : chunkBuffers) {
                if (cdata != null) {
                    fos.write(cdata);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("[DownloadManager] Download complete: hash=" + fileHash);
    }
}
