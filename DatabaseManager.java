import java.sql.*;
import java.time.LocalDateTime;
import java.util.UUID;

public class DatabaseManager {

    private final Connection connection;

    public DatabaseManager(Connection connection) {
        this.connection = connection;
    }

    // 检查玩家是否处于死亡冷却阶段
    public boolean isPlayerInCooldown(UUID playerId) throws SQLException {
        String query = "SELECT death_data FROM player_data WHERE uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, playerId.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String deathData = rs.getString("death_data");
                // 假设 deathData 是 JSON 格式，解析并检查 reviveAt
                LocalDateTime reviveAt = LocalDateTime.parse(deathData.split("\"reviveAt\":\"")[1].split("\"")[0]);
                return LocalDateTime.now().isBefore(reviveAt);
            }
        }
        return false;
    }

    // 返回玩家死亡时间和重生时间
    public LocalDateTime[] getPlayerDeathInfo(UUID playerId) throws SQLException {
        String query = "SELECT death_data FROM player_data WHERE uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, playerId.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String deathData = rs.getString("death_data");
                // 假设 deathData 是 JSON 格式，解析 deathAt 和 reviveAt
                LocalDateTime deathAt = LocalDateTime.parse(deathData.split("\"deathAt\":\"")[1].split("\"")[0]);
                LocalDateTime reviveAt = LocalDateTime.parse(deathData.split("\"reviveAt\":\"")[1].split("\"")[0]);
                return new LocalDateTime[]{deathAt, reviveAt};
            }
        }
        return null;
    }

    // 初始化数据表
    public void initializeDatabase() throws SQLException {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS player_data (
                id INT AUTO_INCREMENT PRIMARY KEY,
                uuid VARCHAR(36) NOT NULL,
                server_name VARCHAR(255),
                death_data TEXT,
                revival_method VARCHAR(255),
                update_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                create_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
        """;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSQL);
        }
    }

    // 检查数据表
    public boolean validateDatabase() {
        try {
            String query = "DESCRIBE player_data";
            try (Statement stmt = connection.createStatement()) {
                stmt.executeQuery(query);
            }
            return true;
        } catch (SQLException e) {
            try {
                initializeDatabase();
                return true;
            } catch (SQLException ex) {
                ex.printStackTrace();
                return false;
            }
        }
    }

    // 插入玩家死亡数据
    public void insertPlayerDeathData(UUID playerId, String serverName, String deathData, String revivalMethod) throws SQLException {
        String insertSQL = """
            INSERT INTO player_data (uuid, server_name, death_data, revival_method)
            VALUES (?, ?, ?, ?);
        """;
        try (PreparedStatement stmt = connection.prepareStatement(insertSQL)) {
            stmt.setString(1, playerId.toString());
            stmt.setString(2, serverName);
            stmt.setString(3, deathData);
            stmt.setString(4, revivalMethod);
            stmt.executeUpdate();
        }
    }

    // 更新玩家冷却时间
    public void updatePlayerCooldown(UUID playerId, LocalDateTime deathAt, LocalDateTime reviveAt, String deathCaused) throws SQLException {
        String deathData = String.format("""
            {
                "deathAt": "%s",
                "reviveAt": "%s",
                "deathCaused": "%s"
            }
        """, deathAt, reviveAt, deathCaused);
        String updateSQL = "UPDATE player_data SET death_data = ? WHERE uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(updateSQL)) {
            stmt.setString(1, deathData);
            stmt.setString(2, playerId.toString());
            stmt.executeUpdate();
        }
    }

    // 重置玩家数据
    public void resetPlayerData(UUID playerId) throws SQLException {
        String deleteSQL = "DELETE FROM player_data WHERE uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(deleteSQL)) {
            stmt.setString(1, playerId.toString());
            stmt.executeUpdate();
        }
    }
}
