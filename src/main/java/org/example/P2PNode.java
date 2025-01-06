package org.example;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class P2PNode {

    // CHUNK_SIZE sabitini 4KB olarak tanımlayın
    private static final int CHUNK_SIZE = 4 * 1024; // 4KB

    private File rootFolder;
    private File destinationFolder;
    private boolean isConnected;
    private String nodeId;
    private DiscoveryService discoveryService;
    private Thread discoveryThread;
    private DatagramSocket udpSocket;

    private final int discoveryPort = 55555;
    private final int chunkTransferPort = 55556;

    private final Map<String, FileMetadata> sharedFiles;
    private Map<String, DownloadManager> activeDownloads = new HashMap<>();
    private final Map<String, Set<PeerInfo>> filePeers = new HashMap<>();
    private final ExecutorService executor;

    private Set<File> excludedSubfolders;
    private MainApp guiRef;

    public P2PNode() {
        this.nodeId = UUID.randomUUID().toString();
        this.sharedFiles = new HashMap<>();
        this.activeDownloads = new HashMap<>();
        this.executor = Executors.newCachedThreadPool();
        this.excludedSubfolders = new HashSet<>();
    }

    public void setGuiRef(MainApp gui) {
        this.guiRef = gui;
    }

    public MainApp getGuiRef() {
        return this.guiRef;
    }

    // nodeId getter'ı
    public String getNodeId() {
        return nodeId;
    }

    public synchronized void connect() {
        if (isConnected) return;
        System.out.println("[P2PNode] Connecting...");

        shareLocalFiles();

        discoveryService = new DiscoveryService(this, discoveryPort);
        discoveryThread = new Thread(discoveryService, "DiscoveryServiceThread");
        discoveryThread.start();

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

    private void shareLocalFiles() {
        sharedFiles.clear();
        if (rootFolder == null || !rootFolder.isDirectory()) {
            System.err.println("[P2PNode] Root folder is invalid or not set.");
            return;
        }
        shareLocalFilesRecursive(rootFolder);
    }

    private void shareLocalFilesRecursive(File dir) {
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

    public void handleIncomingPacket(Packet pkt) {
        if (pkt.getNodeId().equalsIgnoreCase(this.nodeId)) {
            return; // Kendi paketini işlemeden çık
        }
        System.out.println(">> [P2PNode] Received " + pkt.getType()
                + " seq=" + pkt.getSeqNumber()
                + " ttl=" + pkt.getTtl()
                + " from=" + pkt.getSourceIP()
                + " nodeId=" + pkt.getNodeId());

        switch (pkt.getType()) {
            case DISCOVERY:
                handleDiscovery(pkt);
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
                break;
        }
    }

    private void handleDiscovery(Packet pkt) {
        // Discovery paketini işleyin (örn. HELLO)
        // Örnek olarak, sadece loglayın veya gerekirse forward edin
    }

    private void handleSearchRequest(Packet pkt) {
        if (pkt.getSourceIP().equalsIgnoreCase(getLocalIP())) { return; }
        String query = pkt.getMessage().toLowerCase();
        List<FileMetadata> results = new ArrayList<>();
        for (FileMetadata fm : sharedFiles.values()) {
            if (fm.getFileName().toLowerCase().contains(query)) {
                results.add(fm);
            }
        }
        if (!results.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (FileMetadata fm : results) {
                sb.append(fm.getFileName()).append("|")
                        .append(fm.getFileHash()).append("|")
                        .append(fm.getFileSize()).append("\n");
            }
            Packet resp = new Packet(Packet.PacketType.SEARCH_RESPONSE, 1, getLocalIP());
            resp.setMessage(sb.toString());
            resp.setSeqNumber(Packet.getNextSeqNumber());
            // nodeId'yi ayarla
            resp.setNodeId(this.nodeId);
            sendUDP(resp, pkt.getSourceIP(), discoveryPort);
        }
    }

    private void handleSearchResponse(Packet pkt) {
        // format: "filename|hash|size\nfilename2|hash2|size2\n..."
        String data = pkt.getMessage();
        String[] lines = data.split("\n");
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            String[] parts = line.split("\\|");
            if (parts.length >= 3) {
                String fName = parts[0];
                String fHash = parts[1];
                long fSize = Long.parseLong(parts[2]);

                // Bu peer, bu dosyaya sahip
                Set<PeerInfo> peers = filePeers.getOrDefault(fHash, new HashSet<>());
                peers.add(new PeerInfo(pkt.getSourceIP(), fSize));
                filePeers.put(fHash, peers);
            }
        }
        // GUI'ye yansıt
        if (guiRef != null) {
            guiRef.addSearchResults(data);
        }
    }

    private void handleChunkRequest(Packet pkt) {
        String hash = pkt.getFileHash();
        int chunkIndex = pkt.getChunkIndex();
        FileMetadata fm = sharedFiles.get(hash);
        if (fm == null) {
            System.out.println("[P2PNode] We don't have file with hash=" + hash);
            return;
        }
        byte[] chunkData = readChunkFromFile(fm.getFile(), chunkIndex);

        // CHUNK_SIZE'ı küçük tuttuğunuz için artık paket boyutu sınırını aştığınızdan emin olun
        Packet resp = new Packet(Packet.PacketType.CHUNK_RESPONSE, 1, getLocalIP());
        resp.setFileHash(hash);
        resp.setChunkIndex(chunkIndex);
        resp.setChunkData(chunkData);
        resp.setFileSize(fm.getFileSize());
        // nodeId'yi ayarla
        resp.setNodeId(this.nodeId);

        try {
            sendUDP(resp, pkt.getSourceIP(), chunkTransferPort);
            System.out.println("[P2PNode] Sent CHUNK_RESPONSE (hash=" + hash
                    + ", chunk=" + chunkIndex + ") to " + pkt.getSourceIP());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private byte[] readChunkFromFile(File file, int chunkIndex) {
        long offset = (long) chunkIndex * CHUNK_SIZE;
        if (offset >= file.length()) {
            return new byte[0];
        }
        int toRead = (int) Math.min(CHUNK_SIZE, file.length() - offset);
        byte[] buffer = new byte[toRead];
        try (FileInputStream fis = new FileInputStream(file)) {
            fis.skip(offset);
            fis.read(buffer, 0, toRead);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return buffer;
    }

    private void handleChunkResponse(Packet pkt) {
        String hash = pkt.getFileHash();
        DownloadManager dm = activeDownloads.get(hash);
        if (dm != null) {
            dm.handleChunkData(pkt.getChunkIndex(), pkt.getChunkData());
        } else {
            System.out.println("[P2PNode] No active download for hash=" + hash);
        }
    }

    // Yeni yardımcı metod: filePeers içinden o dosyayı paylaşan peer set'ini döndürür
    public Set<PeerInfo> getPeersForFile(String fileHash) {
        return filePeers.getOrDefault(fileHash, Collections.emptySet());
    }

    // Tek parametreli downloadFile:
    public void downloadFile(String fileHash, long fileSize) {
        downloadFile(fileHash, fileSize, false, Collections.emptySet());
    }

    // Çok parametreli (multiSource / singleSource) downloadFile
    public void downloadFile(String fileHash, long fileSize, boolean multiSource, Set<PeerInfo> peers) {
        if (destinationFolder == null) {
            System.out.println("[P2PNode] Destination folder not set!");
            return;
        }
        if (activeDownloads.containsKey(fileHash)) {
            System.out.println("[P2PNode] Already downloading: " + fileHash);
            return;
        }

        DownloadManager dm;
        if (multiSource && peers != null && !peers.isEmpty()) {
            // Çok kaynaklı
            dm = new MultiSourceDownloadManager(this, fileHash, fileSize, destinationFolder, peers);
            System.out.println("[P2PNode] Creating MultiSourceDownloadManager for hash=" + fileHash);
        } else {
            // Tek kaynak
            if (peers != null && !peers.isEmpty()) {
                // İlk peer'i alıyoruz
                PeerInfo firstPeer = peers.iterator().next();
                String remoteIp = firstPeer.getIpAddress();

                dm = new DownloadManager(this, fileHash, fileSize, destinationFolder, remoteIp);
                System.out.println("[P2PNode] Creating single-source DownloadManager for hash=" + fileHash
                        + ", from IP=" + remoteIp);
            } else {
                // Peers boşsa, hangi IP'den indireceğimizi bilmiyoruz
                System.out.println("[P2PNode] No known peers for file " + fileHash + " => cannot single-source download.");
                return;
            }
        }
        activeDownloads.put(fileHash, dm);

        executor.submit(dm::startDownload);
    }

    public void requestChunk(String ip, String hash, int index) {
        Packet req = new Packet(Packet.PacketType.CHUNK_REQUEST, 1, getLocalIP());
        req.setFileHash(hash);
        req.setChunkIndex(index);
        // nodeId'yi ayarla
        req.setNodeId(this.nodeId);
        sendUDP(req, ip, chunkTransferPort);
    }

    public void searchFile(String query) {
        Packet p = new Packet(Packet.PacketType.SEARCH, 2, getLocalIP());
        p.setNodeId(nodeId);
        p.setMessage(query);
        // broadcast
        sendUDP(p, "172.20.10.255", discoveryPort); // Broadcast adresini güncelledik
        System.out.println("[P2PNode] Sent SEARCH -> " + query);
    }

    private void sendUDP(Packet pkt, String ip, int port) {
        try {
            byte[] data = pkt.toBytes();
            DatagramPacket dp = new DatagramPacket(data, data.length, InetAddress.getByName(ip), port);
            if (udpSocket != null && !udpSocket.isClosed()) {
                udpSocket.send(dp);
                System.out.println("[P2PNode] Sent packet type=" + pkt.getType() + " size=" + data.length + " bytes to " + ip + ":" + port);
            }
        } catch (Exception e) {
            System.err.println("[P2PNode] Failed to send packet type=" + pkt.getType() + " to " + ip + ":" + port);
            e.printStackTrace();
        }
    }

    private void chunkListener() {
        System.out.println("[P2PNode] Chunk listener on port " + chunkTransferPort);
        byte[] buf = new byte[8192]; // 8KB buffer
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
        return DiscoveryService.getEffectiveLocalIP();
    }

}
