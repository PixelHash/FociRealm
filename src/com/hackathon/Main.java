package com.hackathon;

import com.hackathon.ui.MainWindow;
import com.hackathon.logic.DatabaseManager;

public class Main {
    public static void main(String[] args) {
        DatabaseManager.initDB();
        
        Thread networkThread = new Thread(() -> {
            System.out.println("🌐 Networking service started...");
        });
        networkThread.setDaemon(true);
        networkThread.start();

        MainWindow.launch(MainWindow.class, args);
    }
}