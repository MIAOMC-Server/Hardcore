package com.miaomc.hardcore.utils;

import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class HardcoreDisplayManager {

    /**
     * 设置玩家为极限模式爱心显示
     *
     * @param player 目标玩家
     */
    @SuppressWarnings("unused")
    public static void setHardcoreHearts(Player player) {
        // 保存玩家当前所在世界的难度
        World world = player.getWorld();
        Difficulty originalDifficulty = world.getDifficulty();

        // 临时将世界设置为极限模式并确保玩家处于生存模式
        world.setHardcore(true);
        player.setGameMode(GameMode.SURVIVAL);

        // 延迟恢复原有难度设置（如果需要）
        // 这里不立即恢复是为了让客户端有时间更新显示
    }

    /**
     * 恢复玩家为普通爱心显示
     *
     * @param player 目标玩家
     */
    @SuppressWarnings("unused")
    public static void restoreNormalHearts(Player player) {
        World world = player.getWorld();
        world.setHardcore(false);
    }
}