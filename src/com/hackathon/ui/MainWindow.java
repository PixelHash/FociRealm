package com.hackathon.ui;

import com.hackathon.logic.AIService;
import com.hackathon.logic.NetworkManager;
import com.hackathon.logic.DatabaseManager;
import com.hackathon.logic.VisionManager;
import com.hackathon.logic.OllamaManager;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.Stage;

public class MainWindow extends Application {
    private OllamaManager ollamaManager;
    private VisionManager visionManager;

    private Stage window;
    private NetworkManager network = new NetworkManager();
    private AIService aiService;

    // Chat UI Components
    private VBox chatPanel;
    private ListView<String> chatHistory;
    
    // Session State
    private boolean isHostMode = false;
    private String hostIpAddress = "127.0.0.1";
    private int currentXP = 0; 
    
    // --- Incremental XP State ---
    private int elapsedSeconds = 0; 
    private int pendingXP = 0;
    
    // Arena UI Components
    private Label timerLabel;
    private Label pendingXpLabel;
    private Label streakLabel;
    private ListView<String> leaderboard;
    private TextArea aiCoachArea;
    private TextField taskInput;
    private Button startBtn;
    private TextField timeInput;
    
    // Timer State
    private int timeRemaining = 25 * 60; 
    private boolean isTimerRunning = false;
    private Thread timerThread;

    private boolean isCheckInActive = false;
    private int checkInTimeRemaining = 0;
    private String currentCheckInCode = "";
    private int timeUntilNextCheckIn = 0;
    private java.util.Random random = new java.util.Random();
    private Button askBtn;

    @Override
    public void init() {
        aiService = new AIService();

        ollamaManager = new OllamaManager();
        ollamaManager.ensureOllamaIsRunning();
        
        visionManager = new VisionManager(() -> {
            Platform.runLater(() -> {
                triggerCheckIn();
                aiCoachArea.setText("👀 DISTRACTION DETECTED! Prove you're still here!");
            });
        });
    }

    @Override
    public void start(Stage primaryStage) {
        this.window = primaryStage;
        window.setTitle("FociRealm - LAN Lobby");
        window.setScene(buildLobbyScene());
        window.show();
    }

    private Scene buildLobbyScene() {
        Label title = new Label("FOCI REALM");
        title.setFont(new Font("Consolas", 40));
        title.setStyle("-fx-text-fill: #00ff88; -fx-font-weight: bold;");
        
        Label subtitle = new Label("LAN Multiplayer Productivity");
        subtitle.setStyle("-fx-text-fill: white; -fx-font-size: 16px;");

        Button hostBtn = new Button("Host New Arena");
        hostBtn.setStyle("-fx-background-color: #ff3366; -fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold; -fx-min-width: 250px;");
        
        Label orLabel = new Label("— OR —");
        orLabel.setStyle("-fx-text-fill: #aaaaaa;");

        TextField ipInput = new TextField();
        ipInput.setPromptText("Enter Host IP Address (e.g. 192.168.1.5)");
        ipInput.setMaxWidth(350);
        ipInput.setStyle("-fx-background-color: #333; -fx-text-fill: white; -fx-alignment: center;");

        Button joinBtn = new Button("Join Arena");
        joinBtn.setStyle("-fx-background-color: #00ff88; -fx-text-fill: #121212; -fx-font-size: 18px; -fx-font-weight: bold; -fx-min-width: 250px;");

        hostBtn.setOnAction(e -> {
            isHostMode = true;
            DatabaseManager.initDB(); 
            currentXP = DatabaseManager.loadXP(network.getCurrentUser());
            network.startHostDatabaseServer(); 
            window.setScene(buildArenaScene());
        });

        joinBtn.setOnAction(e -> {
            if (ipInput.getText().isEmpty()) return;
            isHostMode = false;
            hostIpAddress = ipInput.getText().trim();
            currentXP = network.fetchXpFromHost(hostIpAddress);
            window.setScene(buildArenaScene());
        });

        VBox lobbyLayout = new VBox(20, title, subtitle, hostBtn, orLabel, ipInput, joinBtn);
        lobbyLayout.setAlignment(Pos.CENTER);
        lobbyLayout.setStyle("-fx-background-color: #121212;");
        
        return new Scene(lobbyLayout, 1000, 700);
    }

    private Scene buildArenaScene() {
        window.setTitle(isHostMode ? "FociRealm - HOSTING" : "FociRealm - CONNECTED");

        chatHistory = new ListView<>();
        VBox.setVgrow(chatHistory, Priority.ALWAYS);
        
        TextField chatInput = new TextField();
        chatInput.setPromptText("Type a message...");
        chatInput.setStyle("-fx-prompt-text-fill : #D3D3D3");
        Button sendChatBtn = new Button("Send");

        Runnable sendChatLogic = () -> {
            String msg = chatInput.getText().trim();
            if (!msg.isEmpty()) {
                network.sendChatMessage(msg);
                chatInput.clear();
            }
        };
        sendChatBtn.setOnAction(e -> sendChatLogic.run());
        chatInput.setOnAction(e -> sendChatLogic.run());

        HBox chatInputBox = new HBox(5, chatInput, sendChatBtn);
        HBox.setHgrow(chatInput, Priority.ALWAYS);

        chatPanel = new VBox(10, new Label("💬 LAN Chat"), chatHistory, chatInputBox);
        chatPanel.setPrefWidth(300);
        chatPanel.setPadding(new Insets(15));
        chatPanel.setStyle("-fx-background-color: #1e1e24; -fx-border-color: #333; -fx-border-width: 0 0 0 2;"); 

        leaderboard = new ListView<>(); 
        leaderboard.setPrefWidth(260);
        
        leaderboard.setStyle(
            "-fx-background-color: #1e1e24; " +
            "-fx-control-inner-background: #1e1e24; " +
            "-fx-border-width: 0; " +
            "-fx-selection-bar: transparent; " + 
            "-fx-selection-bar-non-focused: transparent;"
        );

        leaderboard.setCellFactory(param -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("-fx-background-color: #1e1e24;"); // Blend with background
                } else {
                    try {
                        String[] parts = item.split(" - ");
                        String namePart = parts[0]; 
                        String[] rightParts = parts[1].split(" \\(");
                        String xpPart = rightParts[0]; 
                        String statusPart = rightParts[1].replace(")", ""); 

                        Label nameLabel = new Label(namePart);
                        nameLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px; -fx-font-family: 'Consolas';");

                        Label statusLabel = new Label(statusPart.toUpperCase());
                        switch (statusPart) {
                            case "Focusing" -> statusLabel.setStyle("-fx-background-color: #00ff8833; -fx-text-fill: #00ff88; -fx-padding: 2 6; -fx-background-radius: 10; -fx-font-size: 10px; -fx-font-weight: bold;");
                            case "Paused" -> statusLabel.setStyle("-fx-background-color: #ff336633; -fx-text-fill: #ff3366; -fx-padding: 2 6; -fx-background-radius: 10; -fx-font-size: 10px; -fx-font-weight: bold;");
                            default -> statusLabel.setStyle("-fx-background-color: #aaaaaa33; -fx-text-fill: #aaaaaa; -fx-padding: 2 6; -fx-background-radius: 10; -fx-font-size: 10px; -fx-font-weight: bold;");
                        }

                        VBox leftBox = new VBox(5, nameLabel, statusLabel);
                        
                        Label xpLabel = new Label(xpPart);
                        xpLabel.setStyle("-fx-text-fill: #00ff88; -fx-font-weight: bold; -fx-font-size: 16px; -fx-font-family: 'Consolas';");

                        Region spacer = new Region();
                        HBox.setHgrow(spacer, Priority.ALWAYS);

                        HBox card = new HBox(leftBox, spacer, xpLabel);
                        card.setAlignment(Pos.CENTER_LEFT);
                        card.setStyle("-fx-background-color: #2a2a35; -fx-padding: 12; -fx-background-radius: 8;");

                        setGraphic(card);
                        setText(null);
                        setStyle("-fx-background-color: #1e1e24; -fx-padding: 4 0 4 0;"); 

                    } catch (Exception e) {
                        setText(item);
                        setStyle("-fx-text-fill: white;");
                        setGraphic(null);
                    }
                }
            }
        });

        network.startListening(leaderboard, chatHistory);
        network.startBroadcasting();

        Label leaderHeader = new Label("🏆 LAN LEADERBOARD");
        leaderHeader.setFont(new Font("Consolas", 18));
        leaderHeader.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 0 0 10 0;");

        VBox sidebar = new VBox(0, leaderHeader, leaderboard);
        sidebar.setPadding(new Insets(15));
        sidebar.setStyle("-fx-background-color: #1e1e24; -fx-border-color: #333; -fx-border-width: 0 2 0 0;"); 


        timerLabel = new Label("25:00");
        timerLabel.setFont(new Font("Consolas", 100)); 
        timerLabel.setStyle("-fx-text-fill: #00ff88; -fx-font-weight: bold;");

        pendingXpLabel = new Label("Pending: 0 XP (Warmup Required)");
        pendingXpLabel.setFont(new Font("Consolas", 20));
        pendingXpLabel.setStyle("-fx-text-fill: #f39c12; -fx-font-weight: bold;"); 

        timeInput = new TextField("25");
        timeInput.setPrefWidth(60);
        timeInput.setStyle("-fx-font-size: 16px; -fx-background-color: #333; -fx-text-fill: white; -fx-alignment: center;");
        
        timeInput.textProperty().addListener((obs, oldV, newV) -> {
            if (!newV.matches("\\d*")) timeInput.setText(newV.replaceAll("[^\\d]", ""));
            if (!isTimerRunning && !timeInput.getText().isEmpty()) {
                int selectedMins = Integer.parseInt(timeInput.getText());
                timeRemaining = selectedMins * 60;
                timerLabel.setText(String.format("%02d:00", selectedMins));
            }
        });

        startBtn = new Button("▶ Start Focus");
        startBtn.setStyle("-fx-background-color: #00ff88; -fx-text-fill: #121212; -fx-font-weight: bold; -fx-font-size: 16px;");
        startBtn.setOnAction(e -> toggleTimer());

        streakLabel = new Label("Total XP: " + currentXP);
        streakLabel.setStyle("-fx-text-fill: #aaaaaa; -fx-font-size: 16px;");

        HBox controlBox = new HBox(15, new Label("Mins:"), timeInput, startBtn);
        controlBox.setAlignment(Pos.CENTER);
        controlBox.setStyle("-fx-text-fill: white;");

        VBox centerLayout = new VBox(20, new Label("🔥 FOCUS QUEST"), timerLabel, pendingXpLabel, controlBox, streakLabel);
        centerLayout.setAlignment(Pos.CENTER);
        centerLayout.setStyle("-fx-background-color: #121212;"); 

        // --- 4. UPGRADED HACKER TERMINAL (AI Coach) ---
        Label aiHeader = new Label("🧠 AI Coach Terminal");
        aiHeader.setFont(new Font("Consolas", 14));
        aiHeader.setStyle("-fx-text-fill: #aaaaaa; -fx-font-weight: bold;");

        aiCoachArea = new TextArea();
        aiCoachArea.setEditable(false);
        aiCoachArea.setWrapText(true);
        aiCoachArea.setPrefHeight(180); 
        aiCoachArea.setStyle(
            "-fx-control-inner-background: #0a0a0c; " + 
            "-fx-background-color: #0a0a0c; " + 
            "-fx-text-fill: #00ff88; " + 
            "-fx-font-family: 'Consolas'; " + 
            "-fx-font-size: 14px; " + 
            "-fx-padding: 10px;"
        );

        Label promptLabel = new Label("root@focus:~#");
        promptLabel.setStyle("-fx-text-fill: #ff3366; -fx-font-family: 'Consolas'; -fx-font-weight: bold; -fx-font-size: 14px;");

        taskInput = new TextField();
        taskInput.setPromptText("Enter task to break down...");
        taskInput.setStyle(
            "-fx-background-color: transparent; " +
            "-fx-text-fill: white; " +
            "-fx-font-family: 'Consolas'; " +
            "-fx-font-size: 14px; " +
            "-fx-prompt-text-fill: #555555;"
        );
        
        askBtn = new Button("EXECUTE");
        askBtn.setStyle("-fx-background-color: #00ff88; -fx-text-fill: #121212; -fx-font-weight: bold; -fx-font-family: 'Consolas'; -fx-cursor: hand;");
        askBtn.setOnAction(e -> {
            if (isCheckInActive) verifyCheckIn();
            else handleAICoach();
        });

        HBox aiInputBox = new HBox(10, promptLabel, taskInput, askBtn);
        aiInputBox.setAlignment(Pos.CENTER_LEFT);
        aiInputBox.setStyle("-fx-background-color: #121212; -fx-padding: 5 10 5 10; -fx-background-radius: 5;");
        HBox.setHgrow(taskInput, Priority.ALWAYS); 

        VBox aiPanel = new VBox(10, aiHeader, aiCoachArea, aiInputBox);
        aiPanel.setPadding(new Insets(15));
        aiPanel.setStyle("-fx-background-color: #1e1e24; -fx-border-color: #00ff88; -fx-border-width: 2 0 0 0;"); 

        // --- 5. ASSEMBLE BORDERPANE ---
        BorderPane root = new BorderPane();
        root.setLeft(sidebar);
        root.setCenter(centerLayout);
        root.setBottom(aiPanel); 
        root.setStyle("-fx-base: #121212; -fx-control-inner-background: #1e1e24; -fx-text-fill: white;");

        // --- NEW: TOP BAR WITH TOGGLE BUTTON ---
        Button toggleChatBtn = new Button("💬 Open Chat");
        toggleChatBtn.setStyle("-fx-background-color: #444; -fx-text-fill: white; -fx-font-weight: bold;");
        
        toggleChatBtn.setOnAction(e -> {
            if (root.getRight() == null) {
                root.setRight(chatPanel); 
                toggleChatBtn.setText("❌ Close Chat");
                toggleChatBtn.setStyle("-fx-background-color: #ff3366; -fx-text-fill: white; -fx-font-weight: bold;"); 
            } else {
                root.setRight(null); 
                toggleChatBtn.setText("💬 Open Chat");
                toggleChatBtn.setStyle("-fx-background-color: #444; -fx-text-fill: white; -fx-font-weight: bold;");
            }
        });

        HBox topBar = new HBox(toggleChatBtn);
        topBar.setAlignment(Pos.CENTER_RIGHT);
        topBar.setPadding(new Insets(10));
        root.setTop(topBar);

        network.updateMyStatus("Idle", currentXP);
        return new Scene(root, 1100, 750); 
    }

    private void bankPendingXP() {
        if (pendingXP > 0) {
            if (isHostMode) {
                DatabaseManager.addXP(network.getCurrentUser(), pendingXP);
                currentXP = DatabaseManager.loadXP(network.getCurrentUser());
            } else {
                network.sendXpToHost(hostIpAddress, pendingXP);
                currentXP += pendingXP; 
            }
        }
        
        pendingXP = 0;
        elapsedSeconds = 0;
        
        Platform.runLater(() -> {
            streakLabel.setText("Total XP: " + currentXP);
            pendingXpLabel.setText("Pending: 0 XP (Streak Broken!)");
            pendingXpLabel.setStyle("-fx-text-fill: #ff3366; -fx-font-weight: bold;"); 
        });
    }

    // --- TIMER & NETWORK LOGIC ---
    private void toggleTimer() {
        if (timeInput.getText().isEmpty()) return;
        
        if (isTimerRunning) {
            isTimerRunning = false;
            visionManager.stopTracking(); 
            
            startBtn.setText("▶ Resume Focus");
            startBtn.setStyle("-fx-background-color: #00ff88; -fx-text-fill: #121212; -fx-font-weight: bold; -fx-font-size: 16px;");
            timeInput.setDisable(false); 
            
            bankPendingXP(); 
            network.updateMyStatus("Paused", currentXP); 
            
        } else {
            isTimerRunning = true;
            visionManager.startTracking(); 
            
            resetAICoachUI(); 
            aiCoachArea.setText("System Ready. Focus up!");
            
            startBtn.setText("⏸ Pause (Bank XP & Lose Streak)");
            startBtn.setStyle("-fx-background-color: #ff3366; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 16px;");
            timeInput.setDisable(true); 
            
            Platform.runLater(() -> {
                pendingXpLabel.setStyle("-fx-text-fill: #f39c12; -fx-font-weight: bold;");
                pendingXpLabel.setText("Pending: 0 XP (Warmup Required)");
            });

            network.updateMyStatus("Focusing", currentXP); 
            startCountdown();
        }
    }

    private void startCountdown() {
        timeUntilNextCheckIn = random.nextInt(60) + 60; 
        isCheckInActive = false;

        timerThread = new Thread(() -> {
            while (isTimerRunning && timeRemaining > 0) {
                try {
                    Thread.sleep(1000); 
                    timeRemaining--; 
                    elapsedSeconds++; 
                    
                    int elapsedMins = elapsedSeconds / 60;
                    if (elapsedMins >= 5) {
                        double multiplier = 1.0 + ((elapsedMins - 5) / 20.0); 
                        pendingXP = (int) (elapsedMins * multiplier);
                        Platform.runLater(() -> pendingXpLabel.setText(String.format("Pending: +%d XP 🔥 (%.1fx Multiplier)", pendingXP, multiplier)));
                    } else {
                        int warmupLeft = 5 - elapsedMins;
                        Platform.runLater(() -> pendingXpLabel.setText("Pending: 0 XP (Warmup: " + warmupLeft + "m left)"));
                    }
                    
                    network.updateMyStatus("Focusing", currentXP + pendingXP);
                    
                    if (isCheckInActive) {
                        checkInTimeRemaining--;
                        if (checkInTimeRemaining <= 0) Platform.runLater(this::failCheckIn);
                        else Platform.runLater(() -> aiCoachArea.setText("🚨 HUMANITY CHECK! 🚨\nType '" + currentCheckInCode + "' below in " + checkInTimeRemaining + "s!"));
                    } else {
                        timeUntilNextCheckIn--;
                        if (timeUntilNextCheckIn <= 0) Platform.runLater(this::triggerCheckIn);
                    }
                    
                    int minutes = timeRemaining / 60;
                    int seconds = timeRemaining % 60;
                    Platform.runLater(() -> timerLabel.setText(String.format("%02d:%02d", minutes, seconds)));
                    
                } catch (InterruptedException e) { break; }
            }
            
            if (timeRemaining <= 0) {
                Platform.runLater(() -> {
                    isTimerRunning = false;
                    isCheckInActive = false; 
                    resetAICoachUI();
                    
                    bankPendingXP(); 
                    
                    timerLabel.setText("SESSION COMPLETE!");
                    network.updateMyStatus("Idle", currentXP);
                    
                    timeInput.setDisable(false);
                    startBtn.setText("▶ Start Next Focus");
                    startBtn.setStyle("-fx-background-color: #00ff88; -fx-text-fill: #121212; -fx-font-weight: bold; -fx-font-size: 16px;");
                    visionManager.stopTracking();
                    
                    int resetMins = Integer.parseInt(timeInput.getText());
                    timeRemaining = resetMins * 60; 
                });
            }
        });
        timerThread.setDaemon(true);
        timerThread.start();
    }

    // --- ANTI-CHEAT LOGIC ---
    private void triggerCheckIn() {
        isCheckInActive = true;
        checkInTimeRemaining = 30; 
        currentCheckInCode = String.format("%04d", random.nextInt(10000)); 
        java.awt.Toolkit.getDefaultToolkit().beep(); 
        aiCoachArea.setStyle("-fx-control-inner-background: #ff3366; -fx-text-fill: white; -fx-font-weight: bold;");
        askBtn.setText("Submit Code");
        taskInput.setPromptText("Enter the 4-digit code!");
        taskInput.clear();
    }

    private void verifyCheckIn() {
        if (taskInput.getText().trim().equals(currentCheckInCode)) {
            resetAICoachUI(); 
            timeUntilNextCheckIn = random.nextInt(60) + 60; 
            aiCoachArea.setText("✅ Check-in complete! Focus maintained.");
            visionManager.startTracking();
        } else {
            aiCoachArea.appendText("\n❌ Incorrect code. Try again!");
        }
    }

    private void failCheckIn() {
        resetAICoachUI(); 
        aiCoachArea.setText("❌ CHECK-IN FAILED ❌\nTimer paused. You banked your XP, but lost your momentum streak!");
        visionManager.stopTracking();
        if (isTimerRunning) toggleTimer(); 
    }

    private void handleAICoach() {
        String task = taskInput.getText();
        if (task.isEmpty()) return;
        
        aiCoachArea.setText("Thinking...\n");
        taskInput.clear(); 
        
        new Thread(() -> {
            String prompt = task + "\n" +
                            "CRITICAL: Respond in raw plain text ONLY. Do not use any Markdown formatting, asterisks, or hash symbols.";
            
            String response = aiService.ask(prompt);
            String cleanResponse = response.replaceAll("\\*\\*", "").replaceAll("\\*", "").replaceAll("(?m)^#+\\s*", ""); 
            Platform.runLater(() -> aiCoachArea.setText(cleanResponse));
        }).start();
    }

    @Override
    public void stop() {
        visionManager.stopTracking();
        if (ollamaManager != null) ollamaManager.shutdown();
        if (network != null) network.stop();
        System.exit(0);
    }

    private void resetAICoachUI() {
        isCheckInActive = false;
        
        aiCoachArea.setStyle("-fx-control-inner-background: #0a0a0c; -fx-background-color: #0a0a0c; -fx-text-fill: #00ff88; -fx-font-family: 'Consolas'; -fx-font-size: 14px; -fx-padding: 10px;");
        
        askBtn.setText("EXECUTE");
        taskInput.clear();
        taskInput.setPromptText("Enter task to break down...");
        taskInput.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-family: 'Consolas'; -fx-font-size: 14px; -fx-prompt-text-fill: #555555;");
        taskInput.setDisable(false);
    }
}