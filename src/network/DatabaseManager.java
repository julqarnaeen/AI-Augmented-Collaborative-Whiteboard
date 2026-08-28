package network;

import java.sql.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {

    private static final String DB_URL = "jdbc:sqlite:whiteboard.db";

    static {
        try {
            Class.forName("org.sqlite.JDBC");
            initializeDatabase();
        } catch (ClassNotFoundException e) {
            System.err.println("[DatabaseManager] SQLite JDBC Driver not found: " + e.getMessage());
        }
    }

    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    private static void initializeDatabase() {
        String createUsersTable = "CREATE TABLE IF NOT EXISTS users (" +
                "username TEXT PRIMARY KEY, " +
                "password_hash TEXT NOT NULL, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")";

        String createDrawingsTable = "CREATE TABLE IF NOT EXISTS drawings (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "action_type TEXT NOT NULL, " +
                "json_data TEXT NOT NULL, " +
                "timestamp INTEGER NOT NULL" +
                ")";

        String createSavedBoardsTable = "CREATE TABLE IF NOT EXISTS saved_boards (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "username TEXT NOT NULL, " +
                "board_name TEXT NOT NULL, " +
                "json_data TEXT NOT NULL, " +
                "saved_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE(username, board_name)" +
                ")";

        String createBlockedSlangsTable = "CREATE TABLE IF NOT EXISTS blocked_slangs (" +
                "word TEXT PRIMARY KEY, " +
                "added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            
            stmt.execute(createUsersTable);
            stmt.execute(createDrawingsTable);
            stmt.execute(createSavedBoardsTable);
            stmt.execute(createBlockedSlangsTable);
            
            // No default seeding of admin user
            
            System.out.println("[DatabaseManager] SQLite database initialized successfully.");
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Error initializing database: " + e.getMessage());
        }
    }

    public static synchronized boolean registerUser(String username, String password) {
        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            return false;
        }
        String normalizedUser = username.trim().toLowerCase();
        String sql = "INSERT INTO users (username, password_hash) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, normalizedUser);
            pstmt.setString(2, hashPassword(password));
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public static boolean loginUser(String username, String password) {
        if (username == null || password == null) {
            return false;
        }
        String normalizedUser = username.trim().toLowerCase();
        String sql = "SELECT password_hash FROM users WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, normalizedUser);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String expectedHash = rs.getString("password_hash");
                    return expectedHash.equals(hashPassword(password));
                }
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Login error: " + e.getMessage());
        }
        return false;
    }

    // --- Drawing State Persistence Methods ---

    public static synchronized void saveAction(String type, String jsonData) {
        String sql = "INSERT INTO drawings (action_type, json_data, timestamp) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, type);
            pstmt.setString(2, jsonData);
            pstmt.setLong(3, System.currentTimeMillis());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Error saving drawing action: " + e.getMessage());
        }
    }

    public static synchronized void removeLastAction() {
        String sql = "DELETE FROM drawings WHERE id = (SELECT MAX(id) FROM drawings)";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Error performing undo: " + e.getMessage());
        }
    }

    public static synchronized void clearDrawings() {
        String sql = "DELETE FROM drawings";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Error clearing drawings: " + e.getMessage());
        }
    }

    public static List<String> getAllDrawings() {
        List<String> list = new ArrayList<>();
        String sql = "SELECT json_data FROM drawings ORDER BY id ASC";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                list.add(rs.getString("json_data"));
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Error retrieving drawings: " + e.getMessage());
        }
        return list;
    }

    public static synchronized boolean saveBoard(String username, String boardName, String jsonData) {
        String sql = "INSERT OR REPLACE INTO saved_boards (username, board_name, json_data) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username.trim().toLowerCase());
            pstmt.setString(2, boardName.trim());
            pstmt.setString(3, jsonData);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Error saving board: " + e.getMessage());
            return false;
        }
    }

    public static List<String> getSavedBoards(String username) {
        List<String> list = new ArrayList<>();
        String sql = "SELECT board_name FROM saved_boards WHERE username = ? ORDER BY board_name ASC";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username.trim().toLowerCase());
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(rs.getString("board_name"));
                }
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Error listing boards: " + e.getMessage());
        }
        return list;
    }

    public static String loadBoard(String username, String boardName) {
        String sql = "SELECT json_data FROM saved_boards WHERE username = ? AND board_name = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username.trim().toLowerCase());
            pstmt.setString(2, boardName.trim());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("json_data");
                }
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Error loading board: " + e.getMessage());
        }
        return null;
    }

    public static synchronized void addBlockedSlang(String word) {
        String sql = "INSERT OR IGNORE INTO blocked_slangs (word) VALUES (?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, word.trim().toLowerCase());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Error saving blocked slang: " + e.getMessage());
        }
    }

    public static List<String> getAllBlockedSlangs() {
        List<String> list = new ArrayList<>();
        String sql = "SELECT word FROM blocked_slangs ORDER BY word ASC";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(rs.getString("word"));
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Error retrieving blocked slangs: " + e.getMessage());
        }
        return list;
    }

    private static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(password.hashCode());
        }
    }
}
