package org.example;

import java.net.*;
import java.util.HashSet;
import java.util.Set;

public class DiscoveryService implements Runnable {

    private volatile boolean running;
    private final int port;
    private final P2PNode node;
    private DatagramSocket socket;
    private final Set<String> seenPackets;

    private final long broadcastIntervalMs = 5000;

    public DiscoveryService(P2PNode node, int port) {
        this.node = node;
        this.port = port;
        this.seenPackets = new HashSet<>();
    }

    @Override
    public void run() {
        running = true;
        try {
            socket = new DatagramSocket(port);
            socket.setSoTimeout(2000);
            System.out.println("[DiscoveryService] Listening on UDP port " + port);

            long lastBroadcastTime = 0;

            while (running) {
                // Gelen paketleri dinle
                try {
                    byte[] buf = new byte[64 * 1024];
                    DatagramPacket dp = new DatagramPacket(buf, buf.length);
                    socket.receive(dp);

                    Packet pkt = Packet.fromBytes(dp.getData());
                    String sourceKey = dp.getAddress().getHostAddress() + ":" + pkt.getSeqNumber();

                    if (!seenPackets.contains(sourceKey)) {
                        seenPackets.add(sourceKey);

                        if (pkt.getTtl() > 0) {
                            node.handleIncomingPacket(pkt);
                            if (shouldForward(pkt)) {
                                pkt.setTtl(pkt.getTtl() - 1);
                                forwardPacket(pkt);
                            }
                        }
                    }

                } catch (SocketTimeoutException e) {
                    // normal
                }

                // Periyodik HELLO broadcast
                long now = System.currentTimeMillis();
                if (now - lastBroadcastTime > broadcastIntervalMs) {
                    broadcastHello();
                    lastBroadcastTime = now;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            System.out.println("[DiscoveryService] Stopped.");
        }
    }

    private boolean shouldForward(Packet pkt) {
        switch (pkt.getType()) {
            case DISCOVERY:
            case SEARCH:
                return true;
            default:
                return false;
        }
    }

    private void broadcastHello() {
        try {
            Packet pkt = new Packet(Packet.PacketType.DISCOVERY, 2, getLocalIP());
            pkt.setMessage("Hello from " + getLocalIP());
            byte[] data = pkt.toBytes();

            DatagramPacket dp = new DatagramPacket(
                    data, data.length,
                    InetAddress.getByName("255.255.255.255"), port
            );
            socket.send(dp);

            String key = getLocalIP() + ":" + pkt.getSeqNumber();
            seenPackets.add(key);

            System.out.println("[DiscoveryService] Sent HELLO seq=" + pkt.getSeqNumber());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void forwardPacket(Packet pkt) {
        try {
            byte[] data = pkt.toBytes();
            DatagramPacket dp = new DatagramPacket(
                    data, data.length,
                    InetAddress.getByName("255.255.255.255"), port
            );
            socket.send(dp);

            String forwardKey = pkt.getSourceIP() + ":" + pkt.getSeqNumber();
            seenPackets.add(forwardKey);

            System.out.println("[DiscoveryService] Forwarded seq=" + pkt.getSeqNumber()
                    + ", ttl=" + pkt.getTtl());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stopDiscovery() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
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


