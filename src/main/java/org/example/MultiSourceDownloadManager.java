package org.example;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class MultiSourceDownloadManager extends DownloadManager {
    private final List<PeerInfo> peerList;
    public MultiSourceDownloadManager(
            P2PNode node,
            String fileHash,
            long fileSize,
            File destFolder,
            Set<PeerInfo> peers
    ) {

        super(node, fileHash, fileSize, destFolder, "MULTIPLE");

        this.peerList = new ArrayList<>(peers);
    }

    @Override
    public void startDownload() {
        isDownloading = true;
        System.out.println("[MultiSourceDM] Start multi-source download: " + "hash=" + fileHash + ", totalChunks=" + totalChunks + ", #peers=" + peerList.size());

        for (int i = 0; i < totalChunks; i++) {
            PeerInfo selectedPeer = pickRandomPeer();
            System.out.println("[MultiSourceDM] Requesting chunk " + i
                    + " from " + selectedPeer.getIpAddress());

            node.requestChunk(selectedPeer.getIpAddress(), fileHash, i);

            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public synchronized void handleChunkData(int chunkIndex, byte[] data) {
        if (!isDownloading) return;
        if (chunkIndex < 0 || chunkIndex >= totalChunks) return;

        chunkBuffers[chunkIndex] = data;
        chunksReceived++;

        double percent = (chunksReceived * 100.0) / totalChunks;
        System.out.printf("[MultiSourceDM] chunk %d/%d (%.2f%%)\n",
                chunksReceived, totalChunks, percent);

        if (chunksReceived == totalChunks) {
            finalizeDownload();
        }
    }

    @Override
    protected void finalizeDownload() {
        isDownloading = false;

        File outFile = new File(destinationFolder, fileHash + "_downloaded.dat");
        System.out.println("[MultiSourceDM] Finalizing: " + outFile.getAbsolutePath());
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            for (byte[] chunk : chunkBuffers) {
                if (chunk != null) {
                    fos.write(chunk);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("[MultiSourceDM] Download complete, hash=" + fileHash);
    }

    private PeerInfo pickRandomPeer() {
        int idx = ThreadLocalRandom.current().nextInt(peerList.size());
        return peerList.get(idx);
    }
}
