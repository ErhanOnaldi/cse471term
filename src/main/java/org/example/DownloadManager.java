package org.example;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class DownloadManager {

    protected final P2PNode node;
    protected final String fileHash;
    protected final long fileSize;
    protected final File destinationFolder;

    protected int totalChunks;
    protected byte[][] chunkBuffers;
    protected int chunksReceived;
    protected boolean isDownloading;
    private final String remotePeerIP;

    public DownloadManager(P2PNode node, String fileHash, long fileSize, File destFolder, String remotePeerIP) {
        this.node = node;
        this.fileHash = fileHash;
        this.fileSize = fileSize;
        this.destinationFolder = destFolder;
        this.remotePeerIP = remotePeerIP;

        final int CHUNK_SIZE = 256 * 1024;
        this.totalChunks = (int)Math.ceil((double)fileSize / CHUNK_SIZE);
        if (this.totalChunks <= 0) this.totalChunks = 1;

        this.chunkBuffers = new byte[totalChunks][];
        this.chunksReceived = 0;
        this.isDownloading = false;
    }

    public void startDownload() {
        isDownloading = true;
        System.out.println("[DownloadManager] Start download hash=" + fileHash
                + ", size=" + fileSize + ", totalChunks=" + totalChunks
                + ", from=" + remotePeerIP);

        // Artık her chunk isteğini "remotePeerIP" üzerinden yapıyoruz
        for (int i = 0; i < totalChunks; i++) {
            node.requestChunk(remotePeerIP, fileHash, i);
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized void handleChunkData(int index, byte[] data) {
        if (!isDownloading) return;
        if (index < 0 || index >= totalChunks) return;

        chunkBuffers[index] = data;
        chunksReceived++;

        double percent = (chunksReceived * 100.0) / totalChunks;

        // 1) Konsol Log
        System.out.printf("[DownloadManager] chunk %d/%d (%.2f%%)\n",
                chunksReceived, totalChunks, percent);

        // 2) GUI'ye haber ver
        node.getGuiRef().updateDownloadProgress(fileHash, percent);

        if (chunksReceived == totalChunks) {
            finalizeDownload();
        }
    }

    protected void finalizeDownload() {
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
