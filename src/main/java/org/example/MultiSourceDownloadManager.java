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

    private static final int CHUNK_SIZE = 256 * 1024; // 8KB

    /**
     * Burada, üst sınıfın (DownloadManager) constructor'ına
     * "remotePeerIP" parametresi vermemiz gerekiyor. Fakat
     * multi-source indirmede tek bir IP yerine birden fazla
     * ip'den chunk isteyeceğimiz için, placeholder olarak
     * "MULTIPLE" kullanıyoruz.
     */
    public MultiSourceDownloadManager(
            P2PNode node,
            String fileHash,
            long fileSize,
            File destFolder,
            Set<PeerInfo> peers
    ) {
        // DownloadManager'daki yeni constructor'a 5. parametre olarak "MULTIPLE" veriyoruz
        super(node, fileHash, fileSize, destFolder, "MULTIPLE");

        this.peerList = new ArrayList<>(peers);
    }

    @Override
    public void startDownload() {
        isDownloading = true;
        System.out.println("[MultiSourceDM] Start multi-source download: "
                + "hash=" + fileHash
                + ", totalChunks=" + totalChunks
                + ", #peers=" + peerList.size());

        for (int i = 0; i < totalChunks; i++) {
            // Her chunk için rastgele bir peer seçelim
            PeerInfo selectedPeer = pickRandomPeer();
            System.out.println("[MultiSourceDM] Requesting chunk " + i
                    + " from " + selectedPeer.getIpAddress());

            // Üst sınıftaki node.requestChunk(...) çağrısı
            node.requestChunk(selectedPeer.getIpAddress(), fileHash, i);

            try {
                Thread.sleep(10); // İsteği belli aralıklarla gönderiyoruz
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public synchronized void handleChunkData(int chunkIndex, byte[] data) {
        if (!isDownloading) return;
        if (chunkIndex < 0 || chunkIndex >= totalChunks) return;

        if (chunkBuffers[chunkIndex] == null) { // Aynı chunk tekrar işlenmesin
            chunkBuffers[chunkIndex] = data;
            chunksReceived++;

            double percent = (chunksReceived * 100.0) / totalChunks;
            System.out.printf("[MultiSourceDM] chunk %d/%d (%.2f%%)\n",
                    chunksReceived, totalChunks, percent);

            // GUI'ye haber ver
            node.getGuiRef().updateDownloadProgress(fileHash, percent);

            if (chunksReceived == totalChunks) {
                finalizeDownload();
            }
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
        if (peerList.isEmpty()) return null;
        int idx = ThreadLocalRandom.current().nextInt(peerList.size());
        return peerList.get(idx);
    }
}
