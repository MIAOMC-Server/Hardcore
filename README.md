# MIAOMC Hardcore

## 简介

MIAOMC Hardcore是一个Minecraft服务器插件，为游戏添加类似极限模式的死亡惩罚机制。玩家死亡后会进入旁观者模式，并需要经过一段冷却时间或支付资源才能复活。

## 功能特点

- 玩家死亡后自动进入旁观者模式
- 可配置的死亡冷却时间
- 支持使用PlayerPoints代币立即复活
- 自定义复活过程和命令序列
- 支持MySQL数据库存储玩家死亡数据
- 支持PlaceholderAPI，提供多种死亡相关占位符
- 死亡位置记录和传送
- 支持极限模式爱心显示

## 安装方法

1. 将插件JAR文件放入服务器的`plugins`文件夹中
2. 重启服务器或使用插件管理器加载插件
3. 配置`config.yml`文件
4. 确保MySQL数据库连接正常

## 命令列表

| 命令                | 别名                     | 描述                  |
|-------------------|------------------------|---------------------|
| `/mhc help`       | `/hardcore help`       | 显示帮助信息              |
| `/mhc revive`     | `/hardcore revive`     | 在冷却结束后复活            |
| `/mhc revive pay` | `/hardcore revive pay` | 使用代币立即复活            |
| `/mhc reset <玩家>` | `/hardcore reset <玩家>` | 管理员命令 - 重置玩家的死亡冷却状态 |

## 权限节点

- `miaomc.hardcore.use` - 允许使用基本命令
- `miaomc.hardcore.admin` - 允许使用管理员命令

## 配置文件

```yaml
settings:
  serverName: root # 默认 root 用于区分子服
  reviveCooldown: 3600 #设置成0关闭，默认3600秒，复活时间，单位秒(重生冷却时间)
  messagePrefix: '&7[&a硬核模式&7] ' # 消息前缀
  useHardcoreHearts: true  # 是否使用极限模式爱心显示
  keepInventory: false # 是否保留物品
  reviveProcess: # 重生命令序列 {player} 代表玩家名
    - "tell {player} 重生成功！"
  # 将会按顺序执行，最后玩家会被调整为生存模式，因此无需在命令中设置
  reviveNeed: # 复活所需物品
    - playerpoints: 100 # 玩家点数，暂时只有这一项

database:
  host: localhost # 数据库主机地址
  port: 3306 # 数据库端口
  name: dbName # 数据库名称
  username: userName # 数据库用户名
  password: userPassword # 数据库密码
  tablename: hardcoreData # 创建的数据表的名称
```

## PlaceholderAPI 占位符

本插件支持PlaceholderAPI，提供以下占位符:

| 占位符                           | 描述                     |
|-------------------------------|------------------------|
| `%mhc_time_remain%`           | 玩家剩余冷却时间（秒）            |
| `%mhc_time_remain_formatted%` | 格式化的剩余冷却时间 (XX时XX分XX秒) |
| `%mhc_is_coolingdown%`        | 玩家是否在冷却中 (true/false)  |
| `%mhc_revive_needs%`          | 复活所需资源                 |

## 数据库结构

插件使用MySQL数据库存储玩家死亡信息，表结构如下:

| 字段             | 类型           | 描述       |
|----------------|--------------|----------|
| id             | INT          | 自增主键     |
| uuid           | VARCHAR(36)  | 玩家UUID   |
| server_name    | VARCHAR(64)  | 服务器名称    |
| death_data     | TEXT         | 死亡数据JSON |
| revival_method | VARCHAR(128) | 复活方式     |
| handled        | BOOLEAN      | 是否处理完成   |
| update_date    | TIMESTAMP    | 更新时间     |
| create_date    | TIMESTAMP    | 创建时间     |

## 依赖插件

- PlaceholderAPI (可选，用于占位符支持)
- PlayerPoints (可选，用于代币复活功能)

## 常见问题解答

**Q: 玩家死亡后为什么没有进入观察者模式？**  
A: 检查数据库连接是否正常，查看控制台是否有报错。

**Q: 如何修改复活所需时间？**  
A: 在config.yml中修改`settings.reviveCooldown`的值（单位为秒）。

**Q: 如何让玩家死亡后保留物品？**  
A: 在config.yml中设置`settings.keepInventory`为true。
