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

        // 保存死亡位置
        deathLocations.put(playerUUID, player.getLocation().clone());

        // 检查玩家是否已有死亡记录
        Map<String, Object> cooldownInfo = plugin.getMySQL().isPlayerInCooldown(playerUUID);
        if ((boolean) cooldownInfo.get("status")) {
            // 玩家已有死亡记录，不再重复记录
            Messager.sendMessage(playerUUID, "&c你已经处于死亡状态，无需重复记录。");
        } else {
            // 获取当前时间戳（秒）
            long currentTime = System.currentTimeMillis() / 1000;

            // 获取复活冷却时间（秒）
            int cooldownTime = config.getInt("settings.reviveCooldown", 3600); // 默认1小时
            long reviveTime = currentTime + cooldownTime;

            // 使用Paper API获取死亡消息
            Component deathMessageComponent = event.deathMessage();
            String deathCause = deathMessageComponent != null ?
                    LegacyComponentSerializer.legacySection().serialize(deathMessageComponent) :
                    "Unknown";

            // 创建死亡数据
            Map<String, Object> deathDataMap = new HashMap<>();
            deathDataMap.put("deathAt", currentTime);
            deathDataMap.put("reviveAt", reviveTime);
            deathDataMap.put("deathCaused", deathCause);

            // 保存死亡位置信息
            Location deathLoc = player.getLocation();
            deathDataMap.put("deathX", deathLoc.getX());
            deathDataMap.put("deathY", deathLoc.getY());
            deathDataMap.put("deathZ", deathLoc.getZ());
            deathDataMap.put("deathWorld", deathLoc.getWorld().getName());

            // 将死亡数据存入数据库
            String deathDataJson = convertMapToJsonString(deathDataMap);
            plugin.getMySQL().insertPlayerDeathData(playerUUID, deathDataJson, null);

            // 告知玩家复活冷却时间
            Messager.sendDeathMessage(playerUUID, cooldownTime);
        }

        event.setCancelled(true);
        if (event.deathMessage() != null) {
            plugin.getServer().broadcast(event.deathMessage());
        }

        // 手动统计死亡
        player.setStatistic(Statistic.DEATHS, player.getStatistic(Statistic.DEATHS) + 1);

        if (!config.getBoolean("settings.keepInventory", false)) {
            Location deathLoc = player.getLocation();
            ItemStack[] items = player.getInventory().getContents();
            player.getInventory().clear();
            for (ItemStack item : items) {
                if (item != null && !item.getType().isAir()) {
                    player.getWorld().dropItemNaturally(deathLoc, item);
                }
            }
            player.getInventory().clear();
        }


        // 立即重生玩家并设置为旁观者模式（1 tick后执行，确保死亡事件完全处理）
        plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            // 如果玩家仍然在线
            if (player.isOnline()) {
                // 传送到死亡位置
                Location respawnLoc = deathLocations.get(playerUUID);
                player.teleport(Objects.requireNonNullElseGet(respawnLoc, () -> player.getWorld().getSpawnLocation()));

                // 设置为旁观者模式
                player.setGameMode(GameMode.SPECTATOR);
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        Map<String, Object> cooldownInfo = plugin.getMySQL().isPlayerInCooldown(playerUUID);
        if ((boolean) cooldownInfo.get("status")) {
            // 有死亡记录，将重生点设置为死亡位置
            if (deathLocations.containsKey(playerUUID)) {
                event.setRespawnLocation(deathLocations.get(playerUUID));
            }
        }
    }

    // Paper专有事件 - 玩家重生后处理
//    @EventHandler(priority = EventPriority.HIGHEST)
//    public void onPlayerPostRespawn(PlayerPostRespawnEvent event) {
//        Player player = event.getPlayer();
//        UUID playerUUID = player.getUniqueId();
//
//        Map<String, Object> cooldownInfo = plugin.getMySQL().isPlayerInCooldown(playerUUID);
//        if ((boolean) cooldownInfo.get("status")) {
//            // 设置为旁观者模式 - 使用Paper API直接设置，无需调度任务
//            player.setGameMode(GameMode.SPECTATOR);
//        }
//    }

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
            } else {
                json.append(value);
            }

            first = false;
        }

        json.append("}");
        return json.toString();
    }
}