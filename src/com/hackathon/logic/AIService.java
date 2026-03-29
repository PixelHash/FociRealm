package com.hackathon.logic;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class AIService {

    // CRITICAL: Ensure this matches the model you pulled in setup.bat!
    private static final String MODEL_NAME = "llama3.2"; 
    
    // Using 127.0.0.1 instead of localhost to bypass Windows IPv6 routing issues
    private static final String OLLAMA_API_URL = "http://127.0.0.1:11434/api/generate";

    public String ask(String prompt) {
        System.out.println("[AIService] Sending prompt to Ollama...");
        
        try {
            URL url = new URL(OLLAMA_API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            
            // 🚨 MASSIVE TIMEOUTS: Gives the model time to load into RAM on older laptops
            conn.setConnectTimeout(5000); // 5 seconds to connect
            conn.setReadTimeout(60000);   // 60 seconds to generate the answer

            // Clean the user's prompt so it doesn't break the JSON structure
            String safePrompt = prompt.replace("\"", "\\\"").replace("\n", " ");

            // Build the exact JSON payload Ollama expects
            // Note: stream is set to 'false' so we get the whole answer at once
            String jsonInputString = "{"
                    + "\"model\": \"" + MODEL_NAME + "\","
                    + "\"prompt\": \"" + safePrompt + "\","
                    + "\"stream\": false"
                    + "}";

            // Send the request
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            System.out.println("[AIService] Server responded with code: " + responseCode);

            if (responseCode == 200) {
                // Read the successful response
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                
                // Hackathon-style JSON parsing (Zero dependencies required!)
                return extractResponseText(response.toString());
            } else {
                return "❌ AI Error: Server returned code " + responseCode + ". Is the model downloaded?";
            }

        } catch (java.net.SocketTimeoutException e) {
            System.out.println("[AIService] ❌ TIMEOUT ERROR: The AI took too long to think.");
            return "⏳ The AI is warming up! Please try asking your question again in a few seconds.";
        } catch (Exception e) {
            e.printStackTrace();
            return "❌ Connection Failed. Check if Ollama is running in the background.";
        }
    }

    // Helper method to rip the text out of Ollama's JSON response string
    private String extractResponseText(String json) {
        try {
            String targetKey = "\"response\":\"";
            int startIndex = json.indexOf(targetKey);
            if (startIndex == -1) return "Error parsing AI response.";
            
            startIndex += targetKey.length();
            int endIndex = json.indexOf("\",\"", startIndex); // Finds the end of the string
            
            if (endIndex == -1) endIndex = json.lastIndexOf("\""); // Fallback
            
            String rawText = json.substring(startIndex, endIndex);
            
            // Clean up newline formatting
            return rawText.replace("\\n", "\n").replace("\\\"", "\"").replace("\\t", "\t");
        } catch (Exception e) {
            return "Failed to read AI output format.";
        }
    }
}