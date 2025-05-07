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
    private static final int CONFIRMATION_TIMEOUT = 30000; // 30秒确认超时

    public MainCommand(HardCore plugin) {
        super("mhc");
        this.plugin = plugin;

        // 设置命令属性
        this.setDescription("MIAOMCHardcore 主命令");
        this.setUsage("/mhc [子命令]");
        this.setAliases(List.of("hardcore"));
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
        if (!(sender instanceof Player player)) {
            Messager.sendMessage(sender, "&c只有玩家可以使用此命令");
            return;
        }

        UUID playerUUID = player.getUniqueId();
        Map<String, Object> cooldownStatus = plugin.getMySQL().isPlayerInCooldown(playerUUID);
        boolean inCooldown = (Boolean) cooldownStatus.get("status");

        if (inCooldown) {
            // 使用Messager发送剩余时间消息
            long timeRemain = getTimeRemain(cooldownStatus.get("timeRemain"));
            Messager.sendTimeRemainMessage(playerUUID, timeRemain);
            return;
        }

        // 执行复活逻辑
        performRevivalProcess(player, playerUUID, "command.revive", null, true);
    }

    private void handleRevivePayCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Messager.sendMessage(sender, "&c只有玩家可以使用此命令");
            return;
        }

        UUID playerUUID = player.getUniqueId();

        // 检查PlayerPoints插件是否存在
        if (Bukkit.getServer().getPluginManager().getPlugin("PlayerPoints") == null) {
            Messager.sendMessage(player, "&c服务器未安装PlayerPoints插件，无法进行付费复活");
            return;
        }

        // 检查玩家是否在冷却中
        Map<String, Object> cooldownStatus = plugin.getMySQL().isPlayerInCooldown(playerUUID);
        boolean inCooldown = (Boolean) cooldownStatus.get("status");

        if (!inCooldown) {
            Messager.sendMessage(player, "&c你不在复活冷却中，无需支付复活费用");
            return;
        }

        // 获取PlayerPoints实例
        PlayerPoints playerPoints = (PlayerPoints) Bukkit.getServer().getPluginManager().getPlugin("PlayerPoints");
        int requiredPoints = plugin.getConfig().getInt("settings.reviveNeed.playerpoints", 100);

        // 检查是否是确认操作
        if (isConfirmationValid(playerUUID)) {
            handleRevivePayConfirmation(player, playerUUID, playerPoints, requiredPoints);
        } else {
            // 首次输入，显示确认信息
            showPayConfirmation(player, playerUUID, requiredPoints);
        }
    }

    private boolean isConfirmationValid(UUID playerUUID) {
        return revivePayConfirmation.containsKey(playerUUID) &&
                System.currentTimeMillis() - revivePayConfirmation.get(playerUUID) < CONFIRMATION_TIMEOUT;
    }

    private void handleRevivePayConfirmation(Player player, UUID playerUUID, PlayerPoints playerPoints, int requiredPoints) {
        // 检查点数是否足够
        if (playerPoints != null && playerPoints.getAPI().look(playerUUID) >= requiredPoints) {
            // 扣除点数
            playerPoints.getAPI().take(playerUUID, requiredPoints);

            // 执行复活逻辑，使用不同的复活方法标识
            performRevivalProcess(player, playerUUID, "command.revive.pay", null, true);
        } else {
            Messager.sendMessage(player, "&c你没有足够的代币进行付费复活");
        }

        // 移除确认状态
        revivePayConfirmation.remove(playerUUID);
    }

    private void showPayConfirmation(Player player, UUID playerUUID, int requiredPoints) {
        Messager.sendMessage(player, "&e重生需要消耗: " + requiredPoints + " 代币");
        Messager.sendMessage(player, "&e请在30秒内再次输入该命令确认支付");

        // 记录确认时间
        revivePayConfirmation.put(playerUUID, System.currentTimeMillis());

        // 30秒后自动移除确认状态
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin,
                () -> revivePayConfirmation.remove(playerUUID), 30 * 20L);
    }

    /**
     * 执行玩家复活流程
     */
    @SuppressWarnings("SameParameterValue")
    private void performRevivalProcess(Player player, UUID playerUUID, String revivalMethod, String customMessage, boolean isHandled) {
        // 执行重生流程
        List<String> reviveCommands = plugin.getConfig().getStringList("settings.reviveProcess");
        for (String cmd : reviveCommands) {
            String processedCmd = cmd.replace("{player}", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCmd);
        }

        // 设置生存模式
        player.setGameMode(GameMode.SURVIVAL);

        // 安全地恢复玩家血量和饱食度到满值
        player.setHealth(20.0); // 默认最大生命值
        player.setFoodLevel(20);
        player.setSaturation(20f); // 设置饱和度满值

        // 更新数据库
        plugin.getMySQL().updateRevivalMethod(playerUUID, revivalMethod, isHandled);

        // 发送复活消息
        Messager.sendRevivalMessage(playerUUID);

        // 如果有自定义消息，发送复活方式消息
        if (customMessage != null) {
            Messager.sendRevivalMethodMessage(playerUUID, customMessage);
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
        UUID targetUUID = getPlayerUUID(targetName, targetPlayer);

        if (targetUUID == null) {
            Messager.sendMessage(sender, "&c找不到玩家 " + targetName + " 的数据");
            return;
        }

        // 统一处理：仅重置玩家冷却时间，不执行重生流程
        plugin.getMySQL().updateRevivalMethod(targetUUID, "admin.reset", false);
        Messager.sendMessage(sender, "&a已重置玩家 " + targetName + " 的死亡冷却时间");

        // 如果玩家在线，提示他可以使用revive命令
        if (targetPlayer != null) {
            Messager.sendMessage(targetPlayer, "&a管理员已重置你的死亡冷却时间，你现在可以使用 /mhc revive 命令重生");
        }
    }

    private UUID getPlayerUUID(String playerName, Player targetPlayer) {
        if (targetPlayer != null) {
            // 在线玩家直接获取UUID
            return targetPlayer.getUniqueId();
        } else {
            // 离线玩家只能通过数据库获取UUID
            return plugin.getMySQL().getPlayerUUIDByName(playerName);
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
     * 安全地获取剩余时间
     */
    private long getTimeRemain(Object timeRemainObj) {
        return switch (timeRemainObj) {
            case Long l -> l;
            case Integer i -> i.longValue();
            case null, default -> 0L;
        };

    }
}