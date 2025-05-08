package com.miaomc.hardcore.listeners;

import com.miaomc.hardcore.HardCore;
import com.miaomc.hardcore.utils.Messager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Statistic;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class OnPlayerDeath implements Listener {
    private final HardCore plugin;
    private final FileConfiguration config;
    // 用于保存玩家死亡位置
    private final Map<UUID, Location> deathLocations = new ConcurrentHashMap<>();

    public OnPlayerDeath(HardCore plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        Location deathLoc = player.getLocation().clone();

        // 保存死亡位置
        deathLocations.put(playerUUID, deathLoc);

        // 检查玩家是否已有死亡记录
        Map<String, Object> cooldownInfo = plugin.getMySQL().isPlayerInCooldown(playerUUID);
        if ((boolean) cooldownInfo.get("status")) {
            // 玩家已有死亡记录，不再重复记录
            Messager.sendMessage(playerUUID, "&c你已经处于死亡状态，无需重复记录。");
        } else {
            // 获取当前时间戳和复活冷却时间（秒）
            long currentTime = System.currentTimeMillis() / 1000;
            int cooldownTime = config.getInt("settings.reviveCooldown", 3600); // 默认1小时
            long reviveTime = currentTime + cooldownTime;

            // 获取死亡消息
            String deathCause = getDeathMessage(event);

            // 创建并保存死亡数据
            Map<String, Object> deathDataMap = createDeathDataMap(currentTime, reviveTime, deathCause, deathLoc);
            plugin.getMySQL().insertPlayerDeathData(playerUUID, convertMapToJsonString(deathDataMap), null);

            // 告知玩家复活冷却时间
            Messager.sendDeathMessage(playerUUID, cooldownTime);
        }

        // 广播死亡消息
        event.setCancelled(true);
        if (event.deathMessage() != null) {
            plugin.getServer().broadcast(Objects.requireNonNull(event.deathMessage()));
        }

        // 手动统计死亡
        player.setStatistic(Statistic.DEATHS, player.getStatistic(Statistic.DEATHS) + 1);

        // 处理物品掉落
        handleItemDrop(player, deathLoc);

        // 立即重生玩家并设置为旁观者模式
        respawnPlayerAsSpectator(player, playerUUID);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        Map<String, Object> cooldownInfo = plugin.getMySQL().isPlayerInCooldown(playerUUID);
        if ((boolean) cooldownInfo.get("status") && deathLocations.containsKey(playerUUID)) {
            // 有死亡记录，将重生点设置为死亡位置
            event.setRespawnLocation(deathLocations.get(playerUUID));
        }
    }

    /**
     * 获取死亡消息
     */
    private String getDeathMessage(PlayerDeathEvent event) {
        Component deathMessageComponent = event.deathMessage();
        return deathMessageComponent != null ?
                LegacyComponentSerializer.legacySection().serialize(deathMessageComponent) :
                "Unknown";
    }

    /**
     * 创建死亡数据Map
     */
    private Map<String, Object> createDeathDataMap(long currentTime, long reviveTime, String deathCause, Location deathLoc) {
        Map<String, Object> deathDataMap = new HashMap<>();
        deathDataMap.put("deathAt", currentTime);
        deathDataMap.put("reviveAt", reviveTime);
        deathDataMap.put("deathCaused", deathCause);
        deathDataMap.put("deathWorld", deathLoc.getWorld().getName());

        // 创建嵌套的位置对象
        Map<String, String> locationMap = new HashMap<>();

        // 使用String.format保留2位小数，然后转回Double
        locationMap.put("x", String.format("%.2f", deathLoc.getX()));
        locationMap.put("y", String.format("%.2f", deathLoc.getY()));
        locationMap.put("z", String.format("%.2f", deathLoc.getZ()));

        deathDataMap.put("deathLoc", locationMap);

        return deathDataMap;
    }

    /**
     * 处理物品掉落
     */
    private void handleItemDrop(Player player, Location deathLoc) {
        if (!config.getBoolean("settings.keepInventory", false)) {
            ItemStack[] items = player.getInventory().getContents();
            player.getInventory().clear();
            for (ItemStack item : items) {
                if (item != null && !item.getType().isAir()) {
                    player.getWorld().dropItemNaturally(deathLoc, item);
                }
            }
        }
    }

    /**
     * 将玩家设置为旁观者模式并传送到死亡位置
     */
    private void respawnPlayerAsSpectator(Player player, UUID playerUUID) {
        plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            if (player.isOnline()) {
                // 传送到死亡位置
                Location respawnLoc = deathLocations.get(playerUUID);
                player.teleport(Objects.requireNonNullElseGet(respawnLoc,
                        () -> player.getWorld().getSpawnLocation()));

                // 设置为旁观者模式
                player.setGameMode(GameMode.SPECTATOR);
            }
        }, 1L);
    }

    /**
     * 将 Map 转换为 JSON 字符串
     */
    private String convertMapToJsonString(Map<String, Object> map) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                json.append(",");
            }

            json.append("\"").append(entry.getKey()).append("\":");

            Object value = entry.getValue();
            if (value instanceof String) {
                json.append("\"").append(value).append("\"");
            } else if (value instanceof Map) {
                // 递归处理嵌套Map
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) value;
                json.append(convertMapToJsonString(nestedMap));
            } else {
                json.append(value);
            }

            first = false;
        }

        json.append("}");
        return json.toString();
    }
}