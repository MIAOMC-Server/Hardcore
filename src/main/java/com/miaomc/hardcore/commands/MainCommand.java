package com.miaomc.hardcore.commands;

import com.miaomc.hardcore.HardCore;
import com.miaomc.hardcore.utils.Messager;
import org.black_ixx.playerpoints.PlayerPoints;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class MainCommand extends Command {
    private final HardCore plugin;
    private final Map<UUID, Long> revivePayConfirmation = new HashMap<>();

    public MainCommand(HardCore plugin) {
        super("mhc");  // 只保留一个super调用
        this.plugin = plugin;

        // 设置命令属性
        this.setDescription("MIAOMCHardcore 主命令");
        this.setUsage("/mhc [子命令]");
        this.setAliases(List.of("hardcore"));  // 将hardcore设为别名
        this.setPermission("miaomc.hardcore.use");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            sendHelpMessage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "revive":
                if (args.length > 1 && "pay".equalsIgnoreCase(args[1])) {
                    handleRevivePayCommand(sender);
                } else {
                    handleReviveCommand(sender);
                }
                break;
            case "reset":
                handleResetCommand(sender, args);
                break;
            default:
                sendHelpMessage(sender);
                break;
        }
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) throws IllegalArgumentException {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("help");
            completions.add("revive");

            if (sender.hasPermission("miaomc.hardcore.admin")) {
                completions.add("reset");
            }

            return filterCompletions(completions, args[0]);
        } else if (args.length == 2) {
            if ("revive".equalsIgnoreCase(args[0])) {
                completions.add("pay");
                return filterCompletions(completions, args[1]);
            } else if ("reset".equalsIgnoreCase(args[0]) && sender.hasPermission("miaomc.hardcore.admin")) {
                Bukkit.getOnlinePlayers().forEach(player -> completions.add(player.getName()));
                return filterCompletions(completions, args[1]);
            }
        }

        return completions;
    }

    private void sendHelpMessage(CommandSender sender) {
        Messager.sendMessage(sender, "&e===== 难狗模式插件帮助 =====");
        Messager.sendMessage(sender, "&a/mhc help &7- 显示此帮助");
        Messager.sendMessage(sender, "&a/mhc revive &7- 复活（如果你已经经过了冷却时间）");
        Messager.sendMessage(sender, "&a/mhc revive pay &7- 使用资源立即复活");

        if (sender.hasPermission("miaomc.hardcore.admin")) {
            Messager.sendMessage(sender, "&e===== 管理员命令 =====");
            Messager.sendMessage(sender, "&a/mhc reset <玩家> &7- 重置玩家的死亡冷却时间");
        }
    }

    private void handleReviveCommand(CommandSender sender) {
        Map<String, Object> playerData = verifyAndGetPlayer(sender);
        if (!(boolean) playerData.get("success")) {
            return;
        }

        Player player = (Player) playerData.get("player");
        UUID playerUUID = (UUID) playerData.get("uuid");
        boolean inCooldown = (boolean) playerData.get("inCooldown");

        // 安全处理timeRemain值
        Object timeRemainObj = playerData.get("timeRemain");
        long timeRemain = 0L;
        if (timeRemainObj != null) {
            if (timeRemainObj instanceof Long) {
                timeRemain = (Long) timeRemainObj;
            } else if (timeRemainObj instanceof Integer) {
                timeRemain = ((Integer) timeRemainObj).longValue();
            }
        }

        if (inCooldown) {
            // 使用Messager发送剩余时间消息
            Messager.sendTimeRemainMessage(playerUUID, timeRemain);
            return;
        }

        // 执行重生流程
        List<String> reviveCommands = plugin.getConfig().getStringList("reviveProcess");
        for (String cmd : reviveCommands) {
            String processedCmd = cmd.replace("{player}", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCmd);
        }

        // 设置生存模式
        player.setGameMode(GameMode.SURVIVAL);

        // 更新数据库
        plugin.getMySQL().updateRevivalMethod(playerUUID, "command.revive");
        // 使用Messager发送复活消息
        Messager.sendRevivalMessage(playerUUID);
    }

    private void handleRevivePayCommand(CommandSender sender) {
        if (!sender.hasPermission("miaomc.hardcore.revive")) {
            Messager.sendMessage(sender, "&c你没有权限使用此命令!");
            return;
        }

        if (!(sender instanceof Player player)) {
            Messager.sendMessage(sender, "&c只有玩家可以使用此命令");
            return;
        }

        UUID playerUUID = player.getUniqueId();

        // 先检查PlayerPoints插件是否存在
        if (Bukkit.getServer().getPluginManager().getPlugin("PlayerPoints") == null) {
            Messager.sendMessage(player, "&c服务器未安装PlayerPoints插件，无法进行付费复活");
            return;
        }

        // 单独进行数据库查询，避免与PlayerPoints同时使用连接池
        boolean inCooldown;
        try {
            Map<String, Object> cooldownStatus = plugin.getMySQL().isPlayerInCooldown(playerUUID);
            inCooldown = (Boolean) cooldownStatus.get("status");

            if (!inCooldown) {
                Messager.sendMessage(player, "&c你不在复活冷却中，无需支付复活费用");
                return;
            }
        } catch (Exception e) {
            plugin.getLogger().severe("数据库查询出错: " + e.getMessage());
            Messager.sendMessage(player, "&c查询数据时出错，请联系管理员");
            return;
        }

        try {
            // 尝试获取PlayerPoints插件实例
            PlayerPoints playerPoints = (PlayerPoints) Bukkit.getServer().getPluginManager().getPlugin("PlayerPoints");
            int requiredPoints = plugin.getConfig().getInt("settings.reviveNeed.playerpoints", 100);

            // 检查是否是确认操作
            if (revivePayConfirmation.containsKey(playerUUID) &&
                    System.currentTimeMillis() - revivePayConfirmation.get(playerUUID) < 30000) {
                // 先检查点数
                if (playerPoints != null && playerPoints.getAPI().look(player.getUniqueId()) >= requiredPoints) {
                    // 扣除点数
                    playerPoints.getAPI().take(player.getUniqueId(), requiredPoints);

                    // 更新数据库（异步操作）
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> plugin.getMySQL().updateRevivalMethod(playerUUID, "command.revive.pay"));

                    // 设置生存模式
                    player.setGameMode(GameMode.SURVIVAL);

                    // 发送消息
                    Messager.sendRevivalMessage(playerUUID);
                    Messager.sendRevivalMethodMessage(playerUUID, "付费");
                } else {
                    Messager.sendMessage(player, "&c你没有足够的点数进行付费复活");
                }

                // 移除确认状态
                revivePayConfirmation.remove(playerUUID);
            } else {
                // 首次输入，显示确认信息
                Messager.sendMessage(player, "&e重生需要消耗: " + requiredPoints + " 点数");
                Messager.sendMessage(player, "&e请在30秒内再次输入该命令确认支付");

                // 记录确认时间
                revivePayConfirmation.put(playerUUID, System.currentTimeMillis());

                // 30秒后自动移除确认状态
                plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin,
                        () -> revivePayConfirmation.remove(playerUUID), 30 * 20L);
            }
        } catch (Exception e) {
            Messager.sendMessage(player, "&c使用PlayerPoints时出现错误，请联系管理员");
            plugin.getLogger().severe("PlayerPoints插件调用错误: " + e.getMessage());
        }
    }

    private void handleResetCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("miaomc.hardcore.admin")) {
            Messager.sendMessage(sender, "&c你没有权限执行此命令！");
            return;
        }

        if (args.length < 2) {
            Messager.sendMessage(sender, "&c用法: /mhc reset <玩家名>");
            return;
        }

        String targetName = args[1];
        Player targetPlayer = Bukkit.getPlayerExact(targetName);
        UUID targetUUID;

        // 在线玩家直接获取UUID
        if (targetPlayer != null) {
            targetUUID = targetPlayer.getUniqueId();
        } else {
            // 使用数据库查询，而非直接获取离线玩家UUID
            targetUUID = plugin.getMySQL().getPlayerUUIDByName(targetName);

            if (targetUUID == null) {
                Messager.sendMessage(sender, "&c找不到玩家 " + targetName + " 的数据");
                return;
            }
        }

        // 重置玩家冷却时间
        plugin.getMySQL().updateRevivalMethod(targetUUID, "admin.reset");

        Messager.sendMessage(sender, "&a已重置玩家 " + targetName + " 的死亡冷却时间");

        // 如果玩家在线，通知他们
        if (targetPlayer != null) {
            Messager.sendMessage(targetPlayer, "&a管理员已重置你的死亡冷却时间");
        }
    }

    private List<String> filterCompletions(List<String> completions, String input) {
        if (input.isEmpty()) {
            return completions;
        }

        String lowerInput = input.toLowerCase();
        return completions.stream()
                .filter(completion -> completion.toLowerCase().startsWith(lowerInput))
                .collect(Collectors.toList());
    }

    /**
     * 验证发送者是玩家并获取玩家信息
     *
     * @param sender 命令发送者
     * @return 结果包含玩家对象和是否成功
     */
    private Map<String, Object> verifyAndGetPlayer(CommandSender sender) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);

        if (!(sender instanceof Player player)) {
            Messager.sendMessage(sender, "&c只有玩家可以使用此命令");
            return result;
        }

        UUID playerUUID = player.getUniqueId();

        // 检查玩家是否在冷却中
        Map<String, Object> cooldownStatus = plugin.getMySQL().isPlayerInCooldown(playerUUID);

        result.put("success", true);
        result.put("player", player);
        result.put("uuid", playerUUID);
        result.put("inCooldown", cooldownStatus.get("status"));
        result.put("timeRemain", cooldownStatus.get("timeRemain"));

        return result;
    }
}