package com.miaomc.hardcore;

import com.miaomc.hardcore.commands.MainCommand;
import com.miaomc.hardcore.listeners.OnPlayerDeath;
import com.miaomc.hardcore.listeners.OnPlayerJoin;
import com.miaomc.hardcore.utils.MHCPlaceholderHook;
import com.miaomc.hardcore.utils.Messager;
import com.miaomc.hardcore.utils.MySQL;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class HardCore extends JavaPlugin {

    private MySQL mySql;

    @Override
    public void onEnable() {
        // 保存默认配置
        saveDefaultConfig();

        // 初始化MySQL
        mySql = new MySQL(this);
        try {
            mySql.connect();
            if (!mySql.validateDatabase()) {
                getLogger().severe("数据库验证失败，插件将以有限功能运行或禁用");
            }
        } catch (Exception e) {
            getLogger().severe("初始化数据库时出错: " + e.getMessage());
            getLogger().severe("插件将以有限功能运行或禁用");
        }

        Messager.init(this);

        if (getConfig().getBoolean("settings.placeholderOnly", false)) {
            registerPlaceholders();
            getLogger().info("当前配置为仅注册占位符，插件将不会启用其他功能。");
            return;
        }
        
        registerListeners();
        registerCommands();

        registerPlaceholders();

        getLogger().info("Plugin Enabled.");
    }

    @Override
    public void onDisable() {
        // 关闭数据库连接池
        if (mySql != null) {
            mySql.disconnect();
        }
        getLogger().info("Plugin disabled.");
    }

    private void registerPlaceholders() {
        // 首先检查 PlaceholderAPI 插件是否存在
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            // 如果不存在，记录日志并直接返回
            getLogger().info("未找到 PlaceholderAPI，跳过占位符注册");
            return;
        }

        try {
            // 确认 PlaceholderAPI 已加载后再尝试注册占位符
            new MHCPlaceholderHook(this).register();
            getLogger().info("成功注册 PlaceholderAPI 扩展");
        } catch (Exception e) {
            getLogger().warning("注册 PlaceholderAPI 扩展失败: " + e.getMessage());
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new OnPlayerJoin(this), this);
        getServer().getPluginManager().registerEvents(new OnPlayerDeath(this), this);
    }

    private void registerCommands() {
        try {
            MainCommand mainCommand = new MainCommand(this);
            getServer().getCommandMap().register("mhc", mainCommand);
            getServer().getCommandMap().register("hardcore", mainCommand);
            getLogger().info("命令注册成功");
        } catch (Exception e) {
            getLogger().warning("命令注册失败: " + e.getMessage());
        }
    }

    public MySQL getMySQL() {
        return mySql;
    }
}