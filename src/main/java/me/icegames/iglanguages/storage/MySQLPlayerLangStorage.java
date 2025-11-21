package me.icegames.iglanguages.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.ConfigurationSection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static me.icegames.iglanguages.IGLanguages.plugin;

public class MySQLPlayerLangStorage implements PlayerLangStorage {
    private final HikariDataSource dataSource;

    public MySQLPlayerLangStorage(String host, int port, String database, String user, String pass) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database);
        config.setUsername(user);
        config.setPassword(pass);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        ConfigurationSection propsSection = plugin.getConfig().getConfigurationSection("storage.mysql.properties");
        if (propsSection != null) {
            for (String key : propsSection.getKeys(false)) {
                config.addDataSourceProperty(key, propsSection.get(key));
            }
        }

        this.dataSource = new HikariDataSource(config);

        try (Connection connection = dataSource.getConnection();
                java.sql.Statement st = connection.createStatement()) {
            st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS player_langs (uuid VARCHAR(36) PRIMARY KEY, lang VARCHAR(16))");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void savePlayerLang(UUID uuid, String lang) {
        CompletableFuture.runAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement ps = connection.prepareStatement(
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
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement ps = connection.prepareStatement(
                            "SELECT lang FROM player_langs WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next())
                    return rs.getString("lang");
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Boolean> hasPlayerLang(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement ps = connection.prepareStatement(
                            "SELECT 1 FROM player_langs WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                return rs.next();
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return false;
        });
    }

    @Override
    public void removePlayerLang(UUID uuid) {
        CompletableFuture.runAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement ps = connection.prepareStatement(
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
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
