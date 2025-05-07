package com.miaomc.hardcore.utils;

import com.miaomc.hardcore.HardCore;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MHCPlaceholderHook extends PlaceholderExpansion {
    private final HardCore plugin;

    public MHCPlaceholderHook(HardCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "mhc";
    }

    @Override
    public @NotNull String getAuthor() {
        return "MIAOMC";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getClass().getPackage().getImplementationVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String identifier) {
        if (player == null) return "";

        // %mhc_time_remain% - 剩余冷却时间（秒）
        switch (identifier) {
            case "time_remain" -> {
                Map<String, Object> cooldownInfo = plugin.getMySQL().isPlayerInCooldown(player.getUniqueId());
                if ((boolean) cooldownInfo.get("status")) {
                    return String.valueOf(cooldownInfo.get("timeRemain"));
                }
                return "0";
            }

            case "time_remain_formatted" -> {
                Map<String, Object> cooldownInfo = plugin.getMySQL().isPlayerInCooldown(player.getUniqueId());
                if ((boolean) cooldownInfo.get("status")) {
                    long seconds = (long) cooldownInfo.get("timeRemain");
                    long hours = seconds / 3600;
                    long minutes = (seconds % 3600) / 60;
                    long remainingSeconds = seconds % 60;
                    return String.format("%02d时%02d分%02d秒", hours, minutes, remainingSeconds);
                }
                return "00时00分00秒";
            }


            // %mhc_is_coolingdown% - 玩家是否在冷却中（true/false）
            case "is_coolingdown" -> {
                Map<String, Object> cooldownInfo = plugin.getMySQL().isPlayerInCooldown(player.getUniqueId());
                return String.valueOf(cooldownInfo.get("status"));
            }


            // %mhc_revive_needs% - 复活所需物品
            case "revive_needs" -> {
                ConfigurationSection reviveNeedSection = plugin.getConfig().getConfigurationSection("settings.reviveNeed");
                if (reviveNeedSection == null) {
                    return "无需求";
                }

                List<String> needs = new ArrayList<>();
                for (String key : reviveNeedSection.getKeys(false)) {
                    int amount = reviveNeedSection.getInt(key);
                    needs.add(key + ": " + amount);
                }

                return String.join(", ", needs);
            }


            // 保留原有的格式化时间显示
            case "cooldown_formatted" -> {
                Map<String, Object> cooldownInfo = plugin.getMySQL().isPlayerInCooldown(player.getUniqueId());
                if ((boolean) cooldownInfo.get("status")) {
                    long seconds = (long) cooldownInfo.get("timeRemain");
                    long hours = seconds / 3600;
                    long minutes = (seconds % 3600) / 60;
                    long remainingSeconds = seconds % 60;
                    return String.format("%02d:%02d:%02d", hours, minutes, remainingSeconds);
                }
                return "00:00:00";
            }
        }

        return null;
    }
}