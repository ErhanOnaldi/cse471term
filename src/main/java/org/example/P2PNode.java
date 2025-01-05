package org.example;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Ana node yönetim sınıfı.
 * - DiscoveryService başlatma, paylaşılan dosyaları tarama,
 * - Packet'ları karşılama, chunk request/response, search, vs.
 * - GUI'ye (MainApp) callback yapma ("search results" gösterme).
 */
public class P2PNode {

    private File rootFolder;
    private File destinationFolder;
    private boolean isConnected;

    private DiscoveryService discoveryService;
    private Thread discoveryThread;
    private DatagramSocket udpSocket;

    private final int discoveryPort = 55555;
    private final int chunkTransferPort = 55556;

    private final Map<String, FileMetadata> sharedFiles;    // fileHash -> metadata
    private final Map<String, DownloadManager> activeDownloads;

    private final ExecutorService executor;

    // BONUS: Hariç tutulan alt klasörler
    private Set<File> excludedSubfolders;

    // GUI callback: MainApp referansı
    private MainApp guiRef;

    public P2PNode() {
        this.sharedFiles = new HashMap<>();
        this.activeDownloads = new HashMap<>();
        this.executor = Executors.newCachedThreadPool();
        this.excludedSubfolders = new HashSet<>();
    }

    // Bu metotla MainApp'i kaydederiz. Oradan tablo güncellenir.
    public void setGuiRef(MainApp gui) {
        this.guiRef = gui;
    }

    public synchronized void connect() {
        if (isConnected) return;
        System.out.println("[P2PNode] Connecting...");

        // Dosyaları paylaşıma ekle
        shareLocalFiles();

        // DiscoveryService
        discoveryService = new DiscoveryService(this, discoveryPort);
        discoveryThread = new Thread(discoveryService, "DiscoveryServiceThread");
        discoveryThread.start();

        // Chunk transfer portu
        try {
            udpSocket = new DatagramSocket(chunkTransferPort);
            executor.submit(this::chunkListener);
        } catch (Exception e) {
            e.printStackTrace();
        }

        isConnected = true;
        System.out.println("[P2PNode] Connected.");
    }

    public synchronized void disconnect() {
        if (!isConnected) return;
        System.out.println("[P2PNode] Disconnecting...");

        if (discoveryService != null) {
            discoveryService.stopDiscovery();
            discoveryService = null;
        }
        if (discoveryThread != null) {
            try {
                discoveryThread.join(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            discoveryThread = null;
        }
        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
        }

        isConnected = false;
        System.out.println("[P2PNode] Disconnected.");
    }

    public void shutdown() {
        if (isConnected) {
            disconnect();
        }
        executor.shutdownNow();
        System.out.println("[P2PNode] Shutdown complete.");
    }

    // ------------------------------------------------------------------
    // Root / Destination
    // ------------------------------------------------------------------
    public void setRootFolder(File root) {
        this.rootFolder = root;
    }
    public File getRootFolder() {
        return rootFolder;
    }

    public void setDestinationFolder(File dest) {
        this.destinationFolder = dest;
    }
    public File getDestinationFolder() {
        return destinationFolder;
    }

    public void setExcludedSubfolders(Set<File> excluded) {
        this.excludedSubfolders = excluded;
    }

    // ------------------------------------------------------------------
    // Paylaşılan dosyalar
    // ------------------------------------------------------------------
    private void shareLocalFiles() {
        sharedFiles.clear();
        if (rootFolder == null || !rootFolder.isDirectory()) {
            System.err.println("[P2PNode] Root folder is invalid or not set.");
            return;
        }
        shareLocalFilesRecursive(rootFolder);
    }

    private void shareLocalFilesRecursive(File dir) {
        // Hariç tutulan klasör mü?
        if (isFolderExcluded(dir)) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                shareLocalFilesRecursive(f);
            } else {
                FileMetadata fm = new FileMetadata(f);
                sharedFiles.put(fm.getFileHash(), fm);
                System.out.println("[P2PNode] Shared -> " + f.getAbsolutePath()
                        + " [hash=" + fm.getFileHash() + ", size=" + fm.getFileSize() + "]");
            }
        }
    }

    private boolean isFolderExcluded(File dir) {
        for (File ex : excludedSubfolders) {
            if (dir.getAbsolutePath().startsWith(ex.getAbsolutePath())) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Gelen paketleri işleme
    // ------------------------------------------------------------------
    public void handleIncomingPacket(Packet pkt) {
        System.out.println(">> [P2PNode] Received " + pkt.getType()
                + " seq=" + pkt.getSeqNumber()
                + " ttl=" + pkt.getTtl()
                + " from=" + pkt.getSourceIP());

        switch (pkt.getType()) {
            case DISCOVERY:
                // opsiyonel -> DISCOVERY_RESPONSE
                break;

            case SEARCH:
                handleSearchRequest(pkt);
                break;

            case SEARCH_RESPONSE:
                handleSearchResponse(pkt);
                break;

            case CHUNK_REQUEST:
                handleChunkRequest(pkt);
                break;

            case CHUNK_RESPONSE:
                handleChunkResponse(pkt);
                break;

            default:
                // Other
                break;
        }
    }

    // ------------------------------------------------------------------
    // SEARCH
    // ------------------------------------------------------------------
    private void handleSearchRequest(Packet pkt) {
        String query = pkt.getMessage().toLowerCase();
        List<FileMetadata> results = new ArrayList<>();
        for (FileMetadata fm : sharedFiles.values()) {
            if (fm.getFileName().toLowerCase().contains(query)) {
                results.add(fm);
            }
        }
        if (!results.isEmpty()) {
            // Yanıt: her satıra "filename|hash|size"
            StringBuilder sb = new StringBuilder();
            for (FileMetadata fm : results) {
                sb.append(fm.getFileName()).append("|")
                        .append(fm.getFileHash()).append("|")
                        .append(fm.getFileSize()).append("\n");
            }
            Packet resp = new Packet(Packet.PacketType.SEARCH_RESPONSE, 1, getLocalIP());
            resp.setMessage(sb.toString());
            resp.setSeqNumber(Packet.getNextSeqNumber());

            // Unicast geri gönder
            sendUDP(resp, pkt.getSourceIP(), discoveryPort);
        }
    }

    private void handleSearchResponse(Packet pkt) {
        // "filename|hash|size\nfilename2|hash2|size2\n..."
        String data = pkt.getMessage();
        System.out.println("[P2PNode] Got SEARCH_RESPONSE:\n" + data);

        // GUI güncellemesi
        if (guiRef != null) {
            guiRef.addSearchResults(data);
        }
    }

    // ------------------------------------------------------------------
    // CHUNK_REQUEST (upload)
    // ------------------------------------------------------------------
    private void handleChunkRequest(Packet pkt) {
        String hash = pkt.getFileHash();
        int index = pkt.getChunkIndex();
        FileMetadata fm = sharedFiles.get(hash);
        if (fm == null) {
            System.out.println("[P2PNode] We don't have file with hash=" + hash);
            return;
        }
        byte[] chunkData = readChunkFromFile(fm.getFile(), index);

        Packet resp = new Packet(Packet.PacketType.CHUNK_RESPONSE, 1, getLocalIP());
        resp.setFileHash(hash);
        resp.setChunkIndex(index);
        resp.setChunkData(chunkData);

        // Dosya boyutu da ekleyebiliriz (indirme manager belki kullanır)
        resp.setFileSize(fm.getFileSize());

        // Geri gönder
        sendUDP(resp, pkt.getSourceIP(), chunkTransferPort);
        System.out.println("[P2PNode] Sent CHUNK_RESPONSE (hash=" + hash
                + ", chunk=" + index + ") to " + pkt.getSourceIP());
    }

    private byte[] readChunkFromFile(File file, int chunkIndex) {
        final int CHUNK_SIZE = 256 * 1024;
        long offset = (long) chunkIndex * CHUNK_SIZE;
        if (offset >= file.length()) {
            return new byte[0];
        }
        int toRead = (int)Math.min(CHUNK_SIZE, file.length() - offset);
        byte[] buffer = new byte[toRead];
        try (FileInputStream fis = new FileInputStream(file)) {
            fis.skip(offset);
            fis.read(buffer, 0, toRead);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return buffer;
    }

    // ------------------------------------------------------------------
    // CHUNK_RESPONSE (download)
    // ------------------------------------------------------------------
    private void handleChunkResponse(Packet pkt) {
        String hash = pkt.getFileHash();
        int index = pkt.getChunkIndex();
        byte[] data = pkt.getChunkData();

        DownloadManager dm = activeDownloads.get(hash);
        if (dm != null) {
            dm.handleChunkData(index, data);
        } else {
            System.out.println("[P2PNode] No active download for " + hash);
        }
    }

    // ------------------------------------------------------------------
    // DOWNLOAD
    // ------------------------------------------------------------------
    public void downloadFile(String fileHash, long fileSize) {
        if (destinationFolder == null) {
            System.out.println("[P2PNode] Destination folder not set!");
            return;
        }
        if (activeDownloads.containsKey(fileHash)) {
            System.out.println("[P2PNode] Already downloading: " + fileHash);
            return;
        }
        DownloadManager dm = new DownloadManager(this, fileHash, fileSize, destinationFolder);
        activeDownloads.put(fileHash, dm);
        executor.submit(dm::startDownload);
    }

    public void requestChunk(String ip, String hash, int index) {
        Packet req = new Packet(Packet.PacketType.CHUNK_REQUEST, 1, getLocalIP());
        req.setFileHash(hash);
        req.setChunkIndex(index);
        sendUDP(req, ip, chunkTransferPort);
    }

    // ------------------------------------------------------------------
    // SEARCH Gönder
    // ------------------------------------------------------------------
    public void searchFile(String query) {
        Packet p = new Packet(Packet.PacketType.SEARCH, 2, getLocalIP());
        p.setMessage(query);
        sendUDP(p, "255.255.255.255", discoveryPort);
        System.out.println("[P2PNode] Sent SEARCH -> " + query);
    }

    // ------------------------------------------------------------------
    // Yardımcı
    // ------------------------------------------------------------------
    private void sendUDP(Packet pkt, String ip, int port) {
        try {
            byte[] data = pkt.toBytes();
            DatagramPacket dp = new DatagramPacket(data, data.length, InetAddress.getByName(ip), port);
            if (udpSocket != null && !udpSocket.isClosed()) {
                udpSocket.send(dp);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void chunkListener() {
        System.out.println("[P2PNode] Chunk listener on port " + chunkTransferPort);
        byte[] buf = new byte[64 * 1024];
        while (!udpSocket.isClosed()) {
            try {
                DatagramPacket dp = new DatagramPacket(buf, buf.length);
                udpSocket.receive(dp);
                byte[] incoming = Arrays.copyOfRange(dp.getData(), 0, dp.getLength());
                Packet pkt = Packet.fromBytes(incoming);
                handleIncomingPacket(pkt);
            } catch (IOException e) {
                if (udpSocket.isClosed()) {
                    System.out.println("[P2PNode] chunk socket closed");
                } else {
                    e.printStackTrace();
                }
            }
        }
    }

    private String getLocalIP() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
}
