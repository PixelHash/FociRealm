package com.hackathon.ui;

import javafx.application.Application;

public class Launcher {
    public static void main(String[] args) {
        // JavaFX doesn't crash if it's launched from a non-Application class!
        Application.launch(MainWindow.class, args); 
    }
}