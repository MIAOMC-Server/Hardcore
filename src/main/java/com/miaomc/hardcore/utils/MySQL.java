package com.miaomc.hardcore.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miaomc.hardcore.HardCore;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class MySQL {
    private final HardCore plugin;
    private HikariDataSource dataSource;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final String tablename;
    private final String serverName;

    // 常用SQL语句作为常量，减少字符串拼接和重复
    private static final String SQL_SELECT_LATEST_DEATH =
            "SELECT death_data, revival_method, handled FROM `%s` WHERE uuid = ? AND server_name = ? ORDER BY update_date DESC LIMIT 1";
    private static final String SQL_INSERT_DEATH_DATA =
            "INSERT INTO `%s` (uuid, server_name, death_data, revival_method) VALUES (?, ?, ?, ?)";
    private static final String SQL_UPDATE_REVIVAL_METHOD =
            "UPDATE `%s` SET revival_method = ?, handled = ? WHERE uuid = ? AND server_name = ? ORDER BY update_date DESC LIMIT 1";

    /**
     * MySQL 数据库管理工具类构造函数
     *
     * @param plugin HardCore 主插件实例，用于访问配置和日志系统
     */
    public MySQL(HardCore plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();
        this.host = config.getString("database.host");
        this.port = config.getInt("database.port");
        this.database = config.getString("database.name");
        this.username = config.getString("database.username");
        this.password = config.getString("database.password");
        this.tablename = config.getString("database.tablename");
        this.serverName = config.getString("settings.serverName");
    }

    /**
     * 初始化并连接到 MySQL 数据库连接池
     */
    public void connect() {
        try {
            if (dataSource != null && !dataSource.isClosed()) {
                return;
            }

            HikariConfig config = createHikariConfig();
            dataSource = new HikariDataSource(config);
            plugin.getLogger().info("成功连接到数据库");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "无法连接到数据库: " + e.getMessage());
        }
    }

    /**
     * 创建HikariCP配置
     *
     * @return 配置好的HikariConfig对象
     */
    private HikariConfig createHikariConfig() {
        HikariConfig config = new HikariConfig();
        // 基本连接设置
        config.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s?useSSL=false&autoReconnect=true&useUnicode=true&characterEncoding=utf8",
                host, port, database));
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setUsername(username);
        config.setPassword(password);

        // 连接池配置
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(3);
        config.setIdleTimeout(30000);
        config.setConnectionTimeout(10000);
        config.setMaxLifetime(1800000);

        // 连接测试
        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("HardCore-HikariCP");

        return config;
    }

    /**
     * 获取数据库连接
     *
     * @return 数据库连接对象
     * @throws SQLException 如果获取连接失败
     */
    private Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            connect();
        }
        return dataSource.getConnection();
    }

    /**
     * 关闭数据库连接池
     */
    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    /**
     * 初始化数据表，如果表不存在则创建
     * 表结构包含：id, uuid, server_name, death_data, revival_method, update_date, create_date
     */
    public void initializeDatabase() {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {

            String createTableQuery = "CREATE TABLE IF NOT EXISTS `" + tablename + "` (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "uuid VARCHAR(36) NOT NULL, " +
                    "server_name VARCHAR(64) NOT NULL, " +
                    "death_data TEXT, " +
                    "revival_method VARCHAR(128), " +
                    "handled BOOLEAN DEFAULT FALSE, " +
                    "update_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                    "create_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                    ")";

            statement.executeUpdate(createTableQuery);
            plugin.getLogger().info("成功创建或验证数据表: " + tablename);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "初始化数据表时发生错误: " + e.getMessage());
        }
    }

    /**
     * 验证数据库表结构是否符合要求
     *
     * @return 表结构是否有效，如果无效且无法修复则返回false
     */
    public boolean validateDatabase() {
        try (Connection connection = getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();

            // 检查表是否存在
            try (ResultSet tables = metaData.getTables(null, null, tablename, null)) {
                if (!tables.next()) {
                    // 表不存在，创建表
                    plugin.getLogger().info("数据表不存在，正在创建...");
                    initializeDatabase();
                    return true;
                }
            }

            // 检查列是否存在和类型是否正确
            String[] requiredColumns = {"id", "uuid", "server_name", "death_data", "revival_method", "update_date", "create_date"};
            for (String columnName : requiredColumns) {
                try (ResultSet columns = metaData.getColumns(null, null, tablename, columnName)) {
                    if (!columns.next()) {
                        plugin.getLogger().warning("数据表 " + tablename + " 中缺少列: " + columnName);
                        return false;
                    }
                }
            }
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "验证数据表时发生错误: " + e.getMessage());
            return false;
        }
    }

    /**
     * 检查玩家是否处于死亡冷却阶段
     *
     * @param playerId 玩家的UUID
     * @return 包含状态和剩余时间的Map，格式为 {status: Boolean, timeRemain: Long}
     */
    public Map<String, Object> isPlayerInCooldown(UUID playerId) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", false);

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     String.format(SQL_SELECT_LATEST_DEATH, tablename))) {

            statement.setString(1, playerId.toString());
            statement.setString(2, serverName);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    String revivalMethod = resultSet.getString("revival_method");
                    if (revivalMethod != null && !revivalMethod.isEmpty()) {
                        // 如果有revival_method，玩家不在冷却阶段
                        return result;
                    }

                    String deathDataStr = resultSet.getString("death_data");
                    if (deathDataStr != null && !deathDataStr.isEmpty()) {
                        parseDeathData(deathDataStr, result);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "检查玩家冷却时间时发生错误: " + e.getMessage());
        }

        return result;
    }

    /**
     * 解析死亡数据并更新结果Map
     *
     * @param deathDataStr 死亡数据JSON字符串
     * @param result       要更新的结果Map
     */
    private void parseDeathData(String deathDataStr, Map<String, Object> result) {
        try {
            JsonElement element = JsonParser.parseString(deathDataStr);
            JsonObject deathData = element.getAsJsonObject();

            if (deathData.has("reviveAt")) {
                long reviveAtTimestamp = deathData.get("reviveAt").getAsLong();
                long currentTime = System.currentTimeMillis() / 1000;
                if (currentTime < reviveAtTimestamp) {
                    // 玩家在冷却阶段
                    result.put("status", true);
                    result.put("timeRemain", reviveAtTimestamp - currentTime);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "解析死亡数据时出错: " + e.getMessage());
        }
    }

    /**
     * 异步插入玩家死亡数据
     *
     * @param playerId      玩家的UUID
     * @param deathData     死亡数据的JSON字符串，包含deathAt, reviveAt, deathCaused
     * @param revivalMethod 复活方法，如果为null则表示玩家处于死亡冷却状态
     */
    public void insertPlayerDeathData(final UUID playerId, final String deathData, final String revivalMethod) {
        runAsync(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         String.format(SQL_INSERT_DEATH_DATA, tablename))) {

                statement.setString(1, playerId.toString());
                statement.setString(2, serverName);
                statement.setString(3, deathData);
                statement.setString(4, revivalMethod);

                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "插入玩家死亡数据时发生错误: " + e.getMessage());
            }
        });
    }

    /**
     * 异步更新玩家冷却时间
     *
     * @param playerId    玩家的UUID
     * @param deathAt     玩家死亡时间的UNIX时间戳（秒）
     * @param reviveAt    玩家复活时间的UNIX时间戳（秒）
     * @param deathCaused 导致玩家死亡的原因
     */
    @SuppressWarnings("unused")
    public void updatePlayerCooldown(final UUID playerId, final long deathAt, final long reviveAt, final String deathCaused) {
        runAsync(() -> {
            try (Connection connection = getConnection()) {
                String deathDataJson = createDeathDataJson(deathAt, reviveAt, deathCaused);

                // 查询最新记录，更新death_data
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE `" + tablename +
                                "` SET death_data = ? WHERE uuid = ? AND server_name = ? " +
                                "ORDER BY update_date DESC LIMIT 1")) {

                    statement.setString(1, deathDataJson);
                    statement.setString(2, playerId.toString());
                    statement.setString(3, serverName);

                    int rowsAffected = statement.executeUpdate();
                    if (rowsAffected == 0) {
                        // 如果没有现有记录被更新，则插入新记录
                        insertPlayerDeathData(playerId, deathDataJson, null);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "更新玩家冷却时间时发生错误: " + e.getMessage());
            }
        });
    }

    /**
     * 创建死亡数据的JSON字符串
     *
     * @param deathAt     死亡时间
     * @param reviveAt    复活时间
     * @param deathCaused 死亡原因
     * @return JSON字符串
     */
    private String createDeathDataJson(long deathAt, long reviveAt, String deathCaused) {
        JsonObject deathData = new JsonObject();
        deathData.addProperty("deathAt", deathAt);
        deathData.addProperty("reviveAt", reviveAt);
        deathData.addProperty("deathCaused", deathCaused);
        return deathData.toString();
    }

    /**
     * 异步更新玩家的复活方法
     *
     * @param playerId      玩家的UUID
     * @param revivalMethod 玩家的复活方法
     * @param updateHandled 是否更新handled状态为TRUE，默认为false
     */
    public void updateRevivalMethod(final UUID playerId, final String revivalMethod, final boolean updateHandled) {
        runAsync(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         String.format(SQL_UPDATE_REVIVAL_METHOD, tablename))) {

                statement.setString(1, revivalMethod);
                statement.setBoolean(2, updateHandled);
                statement.setString(3, playerId.toString());
                statement.setString(4, serverName);

                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "更新玩家复活方法时发生错误: " + e.getMessage());
            }
        });
    }

    /**
     * 异步更新玩家的复活方法（保持向后兼容）
     *
     * @param playerId      玩家的UUID
     * @param revivalMethod 玩家的复活方法
     */
    @SuppressWarnings("unused")
    public void updateRevivalMethod(final UUID playerId, final String revivalMethod) {
        updateRevivalMethod(playerId, revivalMethod, false);
    }

    /**
     * 检查玩家是否有未处理的死亡记录（有死亡记录但没有revival_method）
     *
     * @param playerUUID 玩家UUID
     * @return 如果有未处理的死亡记录则返回true
     */
    public boolean hasUnhandledDeathRecord(UUID playerUUID) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     String.format(SQL_SELECT_LATEST_DEATH, tablename))) {

            statement.setString(1, playerUUID.toString());
            statement.setString(2, serverName);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    String deathDataStr = resultSet.getString("death_data");
                    boolean handled = resultSet.getBoolean("handled");

                    // 只检查是否有死亡数据且未被处理
                    return deathDataStr != null && !deathDataStr.isEmpty() && !handled;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "检查未处理死亡记录时发生错误: " + e.getMessage());
        }

        return false;
    }

    /**
     * 检查死亡冷却是否已结束
     *
     * @param deathDataStr 死亡数据JSON
     * @return 如果冷却已结束返回true
     */
    public boolean isDeathCooldownEnded(String deathDataStr) {
        try {
            JsonElement element = JsonParser.parseString(deathDataStr);
            JsonObject deathData = element.getAsJsonObject();

            if (deathData.has("reviveAt")) {
                long reviveAtTimestamp = deathData.get("reviveAt").getAsLong();
                long currentTime = System.currentTimeMillis() / 1000;

                // 如果当前时间大于复活时间，说明冷却已结束，但玩家还未手动复活
                return currentTime >= reviveAtTimestamp;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "解析死亡数据时出错: " + e.getMessage());
        }
        return false;
    }

    /**
     * 通过玩家名查询UUID
     *
     * @param playerName 玩家名
     * @return 玩家UUID，如果未找到返回null
     */
    public UUID getPlayerUUIDByName(String playerName) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT uuid FROM players WHERE name = ?")) {

            stmt.setString(1, playerName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return UUID.fromString(rs.getString("uuid"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("从数据库获取玩家UUID时出错: " + e.getMessage());
        }
        return null;
    }

    /**
     * 运行异步任务的工具方法
     *
     * @param task 要执行的任务
     */
    private void runAsync(Runnable task) {
        new BukkitRunnable() {
            @Override
            public void run() {
                task.run();
            }
        }.runTaskAsynchronously(plugin);
    }
}
