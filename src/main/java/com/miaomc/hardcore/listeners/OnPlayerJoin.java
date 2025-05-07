package com.miaomc.hardcore.listeners;

import com.miaomc.hardcore.HardCore;
import com.miaomc.hardcore.utils.HardcoreDisplayManager;
import com.miaomc.hardcore.utils.Messager;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Map;
import java.util.UUID;

public class OnPlayerJoin implements Listener {

    private final HardCore plugin;

    public OnPlayerJoin(HardCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // 检查玩家是否在冷却中
        Map<String, Object> result = plugin.getMySQL().isPlayerInCooldown(playerUUID);
        boolean inCooldown = (boolean) result.get("status");

        if (inCooldown) {
            // 处理冷却中的逻辑（无需修改）
            player.setGameMode(GameMode.SPECTATOR);
            long timeRemain = (long) result.get("timeRemain");
            Messager.sendTimeRemainMessage(playerUUID, timeRemain);
            scheduleRevivalTask(playerUUID, timeRemain);
        } else if (plugin.getMySQL().hasUnhandledDeathRecord(playerUUID)) {
            // 有未处理的死亡记录，设置为观察者模式
            player.setGameMode(GameMode.SPECTATOR);

            // 直接让玩家可以使用重生命令
            // 不依赖deathData和isDeathCooldownEnded
            Messager.sendMessage(playerUUID, "&a你可以重生了！");
            Messager.sendMessage(playerUUID, "&e使用 /mhc revive 命令重生。");
        } else if (plugin.getConfig().getBoolean("settings.useHardcoreHearts", true)) {
            // 正常进入游戏
            HardcoreDisplayManager.setHardcoreHearts(player);
        }
    }

    /**
     * 为玩家设置复活定时任务
     *
     * @param playerUUID 玩家UUID
     * @param timeRemain 剩余时间（秒）
     */
    private void scheduleRevivalTask(UUID playerUUID, long timeRemain) {
        // 将秒转换为tick (1秒 = 20tick)
        long ticksRemain = timeRemain * 20;

        // 调度任务，在冷却结束时执行
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player player = plugin.getServer().getPlayer(playerUUID);
            if (player != null && player.isOnline()) {
                // 检查玩家是否仍在冷却中（双重检查，防止数据被手动修改）
                Map<String, Object> currentStatus = plugin.getMySQL().isPlayerInCooldown(playerUUID);
                if (!(boolean) currentStatus.get("status")) {
                    // 冷却已结束，通知玩家可以复活
                    Messager.sendRevivalMessage(playerUUID);
                }
            }
        }, ticksRemain);

        // 如果冷却时间较长，每隔一段时间提醒玩家
        if (timeRemain > 300) { // 超过5分钟才设置提醒
            long reminderInterval = 300 * 20; // 每5分钟提醒一次

            // 设置周期性提醒任务
            plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
                Player player = plugin.getServer().getPlayer(playerUUID);
                if (player == null || !player.isOnline()) {
                    // 玩家已下线，取消任务
                    task.cancel();
                    return;
                }

                // 再次检查冷却状态
                Map<String, Object> currentStatus = plugin.getMySQL().isPlayerInCooldown(playerUUID);
                if (!(boolean) currentStatus.get("status")) {
                    // 冷却已结束，取消任务
                    task.cancel();
                    return;
                }

                // 发送剩余时间提醒
                long remainingTime = (long) currentStatus.get("timeRemain");
                Messager.sendMessage(playerUUID, "&7距离重生还剩 &e" + Messager.formatTime(remainingTime) + "&7。");
            }, reminderInterval, reminderInterval);
        }
    }
}