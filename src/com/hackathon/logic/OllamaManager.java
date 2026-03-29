package com.hackathon.logic;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class OllamaManager {

    private Process ollamaProcess;

    public void ensureOllamaIsRunning() {
        System.out.println("[OllamaManager] Checking AI engine status...");

        if (isOllamaResponsive()) {
            System.out.println("[OllamaManager] Ollama is already running on port 11434.");
            return;
        }

        System.out.println("[OllamaManager] Ollama not responding. Attempting background boot...");
        bootOllamaServer();
    }

    private boolean isOllamaResponsive() {
        try {
            URL url = new URL("http://127.0.0.1:11434/api/version");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setConnectTimeout(2000); // Only wait 2 seconds
            
            int status = con.getResponseCode();
            return status == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private void bootOllamaServer() {
        new Thread(() -> {
            try {
                // --- NEW CROSS-PLATFORM BOOT LOGIC ---
                boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
                ProcessBuilder pb;
                
                if (isWindows) {
                    // Windows needs to wrap the command in cmd.exe
                    pb = new ProcessBuilder("cmd.exe", "/c", "ollama serve");
                } else {
                    // Linux/Mac can run it directly
                    pb = new ProcessBuilder("ollama", "serve");
                }
                
                pb.redirectErrorStream(true); 
                ollamaProcess = pb.start();

                System.out.println("[OllamaManager] System process 'ollama serve' executed.");
                
                Thread.sleep(3000);
                if (isOllamaResponsive()) {
                    System.out.println("[OllamaManager] Success! AI Engine is now LIVE.");
                } else {
                    System.out.println("[OllamaManager] ❌ ERROR: Failed to start Ollama.");
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(ollamaProcess.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.toLowerCase().contains("error")) {
                        System.out.println("[Ollama Process] " + line);
                    }
                }
            } catch (Exception e) {
                System.out.println("[OllamaManager] ❌ CRITICAL: Could not execute 'ollama' command.");
            }
        }).start();
    }

    public void shutdown() {
        System.out.println("[OllamaManager] Shutting down background AI server...");
        try {
            boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
            
            if (isWindows) {
                // WINDOWS: Force kill the actual executable so it doesn't become a memory leak
                new ProcessBuilder("taskkill", "/F", "/IM", "ollama.exe").start();
            } else {
                // LINUX/MAC: Java's native destroy works perfectly
                if (ollamaProcess != null && ollamaProcess.isAlive()) {
                    ollamaProcess.destroy();
                }
            }
            System.out.println("[OllamaManager] AI server successfully terminated.");
        } catch (Exception e) {
            System.out.println("[OllamaManager] ❌ Failed to shut down Ollama cleanly.");
        }
    }
}