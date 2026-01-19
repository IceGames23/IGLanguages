package me.icegames.iglanguages.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class SQLitePlayerLangStorage implements PlayerLangStorage {
    private final HikariDataSource dataSource;

    public SQLitePlayerLangStorage(String dbPath) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbPath);
        config.setMaximumPoolSize(1); // SQLite only supports 1 writer
        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("IGLanguages-SQLite");

        this.dataSource = new HikariDataSource(config);

        try (Connection conn = dataSource.getConnection();
                Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS player_langs (uuid TEXT PRIMARY KEY, lang TEXT)");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize SQLite database", e);
        }
    }

    @Override
    public void savePlayerLang(UUID uuid, String lang) {
        CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                    PreparedStatement ps = conn.prepareStatement(
                            "REPLACE INTO player_langs (uuid, lang) VALUES (?, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, lang);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public CompletableFuture<String> getPlayerLang(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                    PreparedStatement ps = conn.prepareStatement(
                            "SELECT lang FROM player_langs WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("lang");
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Boolean> hasPlayerLang(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                    PreparedStatement ps = conn.prepareStatement(
                            "SELECT 1 FROM player_langs WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return false;
        });
    }

    @Override
    public void removePlayerLang(UUID uuid) {
        CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                    PreparedStatement ps = conn.prepareStatement(
                            "DELETE FROM player_langs WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
