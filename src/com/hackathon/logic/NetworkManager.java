package com.hackathon.logic;

import javafx.application.Platform;
import javafx.scene.control.ListView;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Comparator;

public class NetworkManager {
    private static final int PORT = 8888;
    private boolean isRunning = true;
    
    // --- NEW: The Private Room Tag ---
    private String roomCode = "Public"; 
    
    private final String currentUser = System.getProperty("user.name"); 
    private String currentStatus = "Idle";
    private int currentXP = 0;

    public static class PeerRecord {
        public String name;
        public String status;
        public int xp;
        public long lastSeen; 

        public PeerRecord(String name, String status, int xp) {
            this.name = name;
            this.status = status;
            this.xp = xp;
            this.lastSeen = System.currentTimeMillis(); 
        }
    }

    private final ConcurrentHashMap<String, PeerRecord> activePeers = new ConcurrentHashMap<>();

    // --- NEW: Setter for the Room Code ---
    public void setRoomCode(String roomCode) {
        this.roomCode = roomCode;
    }

    public void updateMyStatus(String status, int xp) {
        this.currentStatus = status;
        this.currentXP = xp;
    }

    public void startBroadcasting() {
        Thread broadcastThread = new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setBroadcast(true);
                InetAddress broadcastAddress = InetAddress.getByName("255.255.255.255");

                while (isRunning) {
                    // PREFIX ADDED: "RoomCode|STATUS|..."
                    String payload = roomCode + "|STATUS|" + currentUser + "|" + currentStatus + "|" + currentXP;
                    byte[] buffer = payload.getBytes();
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length, broadcastAddress, PORT);
                    socket.send(packet);
                    Thread.sleep(3000); 
                }
            } catch (Exception e) {
                if (isRunning) System.out.println("Broadcast error: " + e.getMessage());
            }
        });
        broadcastThread.setDaemon(true); 
        broadcastThread.start();
    }

    public void startListening(ListView<String> leaderboardUI, ListView<String> chatUI) {
        Thread listenerThread = new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(PORT)) {
                socket.setSoTimeout(2000); 
                byte[] buffer = new byte[1024];

                while (isRunning) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        socket.receive(packet); 

                        String rawMessage = new String(packet.getData(), 0, packet.getLength());
                        String senderIP = packet.getAddress().getHostAddress();
                        
                        // --- NEW: Room Code Filtering ---
                        int firstPipeIndex = rawMessage.indexOf('|');
                        if (firstPipeIndex == -1) continue; // Malformed packet, ignore
                        
                        String incomingRoom = rawMessage.substring(0, firstPipeIndex);
                        if (!incomingRoom.equals(this.roomCode)) {
                            continue; // DROP PACKET: Belongs to a different Arena!
                        }
                        
                        // Strip the room tag off so the rest of the logic works perfectly
                        String message = rawMessage.substring(firstPipeIndex + 1);
                        
                        // Limit split to 4 parts for STATUS, 3 parts for CHAT
                        String[] parts = message.split("\\|", 4); 
                        
                        if (parts.length > 0) {
                            if (parts[0].equals("STATUS") && parts.length == 4) {
                                String name = parts[1];
                                String status = parts[2];
                                int xp = Integer.parseInt(parts[3].trim()); 

                                activePeers.put(senderIP, new PeerRecord(name, status, xp));
                                refreshLeaderboardUI(leaderboardUI); 
                                
                            } else if (parts[0].equals("CHAT") && parts.length >= 3) {
                                String senderName = parts[1];
                                String chatMsg = parts[2];
                                Platform.runLater(() -> {
                                    chatUI.getItems().add(senderName + ": " + chatMsg);
                                    chatUI.scrollTo(chatUI.getItems().size() - 1); 
                                });
                            }
                        }
                    } catch (java.net.SocketTimeoutException timeout) {
                        long now = System.currentTimeMillis();
                        boolean removedAny = activePeers.entrySet().removeIf(entry -> 
                            (now - entry.getValue().lastSeen) > 10000 
                        );
                        if (removedAny) refreshLeaderboardUI(leaderboardUI); 
                    }
                }
            } catch (Exception e) {
                if (isRunning) System.out.println("Listener error: " + e.getMessage());
            }
        });
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public void sendChatMessage(String message) {
        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setBroadcast(true);
                InetAddress broadcastAddress = InetAddress.getByName("255.255.255.255");
                
                // PREFIX ADDED: "RoomCode|CHAT|..."
                String payload = roomCode + "|CHAT|" + currentUser + "|" + message;
                byte[] buffer = payload.getBytes();
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, broadcastAddress, PORT);
                socket.send(packet);
            } catch (Exception e) {
                System.out.println("Chat error: " + e.getMessage());
            }
        }).start();
    }

    private void refreshLeaderboardUI(ListView<String> leaderboardUI) {
        Platform.runLater(() -> {
            leaderboardUI.getItems().clear();

            activePeers.values().stream()
                .sorted(Comparator.comparingInt((PeerRecord p) -> p.xp).reversed())
                .forEach(p -> {
                    String emoji = p.status.equals("Focusing") ? "🔥" : (p.status.equals("Paused") ? "⏸️" : "💤");
                    leaderboardUI.getItems().add(emoji + " " + p.name + " - " + p.xp + " XP (" + p.status + ")");
                });
        });
    }

    public void stop() {
        isRunning = false;
    }

    public String getCurrentUser() {
        return currentUser;
    }

    public void startHostDatabaseServer() {
        Thread tcpServerThread = new Thread(() -> {
            try (java.net.ServerSocket serverSocket = new java.net.ServerSocket(8889)) {
                System.out.println("Host Database Server running on port 8889...");
                while (isRunning) {
                    try (java.net.Socket client = serverSocket.accept();
                         java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(client.getInputStream()));
                         java.io.PrintWriter out = new java.io.PrintWriter(client.getOutputStream(), true)) {
                        
                        String request = in.readLine();
                        if (request == null) continue;

                        String[] parts = request.split("\\|");
                        String action = parts[0];
                        String user = parts[1];

                        if (action.equals("GET_XP")) {
                            int xp = DatabaseManager.loadXP(user);
                            out.println(xp);
                        } else if (action.equals("UPDATE_XP")) {
                            int earnedXP = Integer.parseInt(parts[2]);
                            DatabaseManager.addXP(user, earnedXP);
                        }
                    }
                }
            } catch (Exception e) {
                if (isRunning) System.out.println("TCP Server Error: " + e.getMessage());
            }
        });
        tcpServerThread.setDaemon(true);
        tcpServerThread.start();
    }

    public int fetchXpFromHost(String hostIP) {
        try (java.net.Socket socket = new java.net.Socket(hostIP, 8889);
             java.io.PrintWriter out = new java.io.PrintWriter(socket.getOutputStream(), true);
             java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream()))) {
            
            out.println("GET_XP|" + currentUser);
            return Integer.parseInt(in.readLine());
        } catch (Exception e) {
            System.out.println("Could not reach Host DB: " + e.getMessage());
            return 0;
        }
    }

    public void sendXpToHost(String hostIP, int earnedXP) {
        new Thread(() -> {
            try (java.net.Socket socket = new java.net.Socket(hostIP, 8889);
                 java.io.PrintWriter out = new java.io.PrintWriter(socket.getOutputStream(), true)) {
                
                out.println("UPDATE_XP|" + currentUser + "|" + earnedXP);
            } catch (Exception e) {
                System.out.println("Could not save XP to Host: " + e.getMessage());
            }
        }).start();
    }
}