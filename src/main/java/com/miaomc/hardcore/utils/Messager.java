package com.miaomc.hardcore.utils;

import com.miaomc.hardcore.HardCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public class Messager {
    private static HardCore plugin;
    private static final String DEFAULT_PREFIX = "&7[&a难狗模式&7] ";


    /**
     * 初始化 Messager 类
     * 应该在插件主类的 onEnable() 方法中调用
     *
     * @param instance 插件实例
     */
    public static void init(HardCore instance) {
        plugin = instance;
    }

    /**
     * 将时间（秒）格式化为可读字符串
     *
     * @param seconds 总秒数
     * @return 格式化的时间字符串
     */
    public static String formatTime(long seconds) {
        if (seconds < 60) {
            return seconds + "秒";
        } else if (seconds < 3600) {
            long mins = seconds / 60;
            long secs = seconds % 60;
            if (secs == 0) {
                return mins + "分钟";
            }
            return mins + "分" + secs + "秒";
        } else if (seconds < 86400) {
            long hours = seconds / 3600;
            long mins = (seconds % 3600) / 60;
            if (mins == 0) {
                return hours + "小时";
            }
            return hours + "小时" + mins + "分钟";
        } else {
            long days = seconds / 86400;
            long hours = (seconds % 86400) / 3600;
            if (hours == 0) {
                return days + "天";
            }
            return days + "天" + hours + "小时";
        }
    }

    /**
     * 获取消息前缀
     *
     * @return 格式化的消息前缀
     */
    public static String getPrefix() {
        if (plugin == null) {
            return colorize(DEFAULT_PREFIX);
        }
        String prefix = plugin.getConfig().getString("settings.messagePrefix", DEFAULT_PREFIX);
        return colorize(prefix);
    }

    /**
     * 将颜色代码（&）转换为Minecraft颜色格式
     *
     * @param message 原始消息
     * @return 带颜色的消息
     */
    public static String colorize(String message) {
        if (message == null) return "";
        return message.replace("&", "§");
    }

    /**
     * 向玩家发送消息
     *
     * @param player  玩家对象
     * @param message 消息内容
     */
    public static void sendMessage(Player player, String message) {
        if (player != null && player.isOnline()) {
            player.sendMessage(colorize(getPrefix() + message));
        }
    }

    /**
     * 通过UUID向玩家发送消息
     *
     * @param playerId 玩家UUID
     * @param message  消息内容
     */
    public static void sendMessage(UUID playerId, String message) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            sendMessage(player, message);
        }
    }

    /**
     * 发送剩余时间消息
     *
     * @param playerId   玩家UUID
     * @param timeRemain 剩余时间（秒）
     */
    public static void sendTimeRemainMessage(UUID playerId, long timeRemain) {
        String formattedTime = formatTime(timeRemain);
        sendMessage(playerId, "&c你仍在死亡冷却中，还需等待 &e" + formattedTime + " &c才能复活！");
    }

    /**
     * 发送死亡消息
     *
     * @param playerId     玩家UUID
     * @param cooldownTime 冷却时间（秒）
     */
    public static void sendDeathMessage(UUID playerId, int cooldownTime) {
        String formattedTime = formatTime(cooldownTime);
        sendMessage(playerId, "&c你已死亡，将在 &e" + formattedTime + " &c后才能复活！");
    }

    /**
     * 发送复活消息
     *
     * @param playerId 玩家UUID
     */
    public static void sendRevivalMessage(UUID playerId) {
        sendMessage(playerId, "&a你已复活，祝游戏愉快！");
    }

    /**
     * 发送复活方式通知
     *
     * @param playerId 玩家UUID
     * @param method   复活方式
     */
    public static void sendRevivalMethodMessage(UUID playerId, String method) {
        sendMessage(playerId, "&a你通过 &e" + method + " &a方式复活了！");
    }

    /**
     * 发送广播消息给所有玩家
     *
     * @param message 消息内容
     */
    public static void broadcastMessage(String message) {
        Bukkit.getOnlinePlayers().forEach(player ->
                sendMessage(player, message));
    }

    /**
     * 向命令发送者发送消息（可以是玩家或控制台）
     *
     * @param sender  命令发送者
     * @param message 消息内容
     */
    public static void sendMessage(org.bukkit.command.CommandSender sender, String message) {
        if (sender != null) {
            sender.sendMessage(colorize(getPrefix() + message));
        }
    }
}