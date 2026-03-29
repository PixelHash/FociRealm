package com.hackathon.logic;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:focus_arena.db";

    public static void initDB() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            
            String sql = "CREATE TABLE IF NOT EXISTS profile (" +
                         "username TEXT PRIMARY KEY, " +
                         "xp INTEGER DEFAULT 0)";
            stmt.execute(sql);
        } catch (Exception e) {
            System.out.println("Database Init Error: " + e.getMessage());
        }
    }

    public static int loadXP(String requestedUser) {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement("SELECT xp FROM profile WHERE username = ?")) {
            
            pstmt.setString(1, requestedUser);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt("xp");
            } else {
                // If user doesn't exist yet, create them at 0 XP
                try (PreparedStatement insert = conn.prepareStatement("INSERT INTO profile (username, xp) VALUES (?, 0)")) {
                    insert.setString(1, requestedUser);
                    insert.executeUpdate();
                }
                return 0;
            }
        } catch (Exception e) {
            System.out.println("Database Load Error: " + e.getMessage());
        }
        return 0;
    }

    public static void addXP(String requestedUser, int earnedXP) {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement("UPDATE profile SET xp = xp + ? WHERE username = ?")) {
            
            pstmt.setInt(1, earnedXP);
            pstmt.setString(2, requestedUser);
            pstmt.executeUpdate();
            
        } catch (Exception e) {
            System.out.println("Database Save Error: " + e.getMessage());
        }
    }
}