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

    private static final int CHUNK_SIZE = 4 * 1024;

    public DownloadManager(P2PNode node, String fileHash, long fileSize, File destFolder, String remotePeerIP) {
        this.node = node;
        this.fileHash = fileHash;
        this.fileSize = fileSize;
        this.destinationFolder = destFolder;
        this.remotePeerIP = remotePeerIP;
        this.totalChunks = (int) Math.ceil((double) fileSize / CHUNK_SIZE);
        if (this.totalChunks <= 0) this.totalChunks = 1;

        this.chunkBuffers = new byte[totalChunks][];
        this.chunksReceived = 0;
        this.isDownloading = false;
    }

    public void startDownload() {
        isDownloading = true;
        System.out.println("[DownloadManager] Start download hash=" + fileHash + ", size=" + fileSize + ", totalChunks=" + totalChunks + ", from=" + remotePeerIP);

        for (int i = 0; i < totalChunks; i++) {
            requestChunkWithRetry(i);
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void requestChunkWithRetry(int chunkIndex) {
        final int MAX_RETRIES = 5;
        final int RETRY_DELAY_MS = 1000;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            node.requestChunk(remotePeerIP, fileHash, chunkIndex);
            System.out.println("[DownloadManager] Requested chunk " + chunkIndex + ", attempt " + attempt);

            try {
                Thread.sleep(RETRY_DELAY_MS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            if (chunkBuffers[chunkIndex] != null) {
                System.out.println("[DownloadManager] Chunk " + chunkIndex + " received.");
                break;
            }

            if (attempt == MAX_RETRIES) {
                System.out.println("[DownloadManager] Failed to download chunk " + chunkIndex + " after " + MAX_RETRIES + " attempts.");
            }
        }
    }

    public synchronized void handleChunkData(int index, byte[] data) {
        if (!isDownloading) return;
        if (index < 0 || index >= totalChunks) {
            System.out.println("[DownloadManager] Invalid chunk index: " + index);
            return;
        }

        if (chunkBuffers[index] == null) {
            chunkBuffers[index] = data;
            chunksReceived++;
            System.out.println("[DownloadManager] Received chunk " + index + "/" + (totalChunks - 1));

            double percent = (chunksReceived * 100.0) / totalChunks;
            node.getGuiRef().updateDownloadProgress(fileHash, percent);

            if (chunksReceived == totalChunks) {
                finalizeDownload();
            }
        } else {
            System.out.println("[DownloadManager] Duplicate chunk received: " + index);
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
        node.getGuiRef().updateDownloadProgress(fileHash, 100.0);
    }
}
