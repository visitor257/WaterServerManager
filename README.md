# WaterServerManager

Minecraft **1.21.1 / Fabric** 服务端模组，给离线模式服务器补上“内部账号 + 正版信息还原 + 玩家数据隔离 + YSM 绑定”。

## 功能

- 内部账号：`/register`、`/login`、`/signin`（令牌）、`/token`、`/account`
- 未登录玩家冻结在 limbo：旁观、不能动、不能聊，显示 `未登录n`
- 密码使用 PBKDF2-HMAC-SHA256 + 随机盐，只存哈希
- 按账号保存/恢复：背包、盔甲、耐久、血量、饥饿、经验、药水效果、着火、末影箱、属性、出生点、坐标/维度、成就
- 离线服正版皮肤还原（按名字查 Mojang 档案）
- 玩家显示名/Tab 名字使用账户名
- YSM 模型绑定：`/wsm model <玩家> <模型>`
- UUID 模式可切换：
  - 固定（默认）：YSM 缓存命中，同名后来者顶掉先来者
  - 随机：同名可共存，YSM 每次进服同步
  - 正版优先：固定 UUID + 启发式正版判定
- 隐藏“加入了游戏/退出了游戏”提示

## 构建

需要 JDK 21 和 Gradle 8.10+：

```bash
gradle build
```

产物：`build/libs/waterservermanager-0.1.0.jar`

## 安装

放到服务器的 `mods/`，同时需要：

- Fabric Loader 0.16.x
- Fabric API 0.116.x（1.21.1）
- 可选：Yes Steve Model（YSM）

配置目录：`config/craftfriend/`

## 常用指令

| 指令 | 说明 |
|---|---|
| `/register <账户名> <密码> <重复密码> [邀请码]` | 注册 |
| `/login <账户名> <密码>` | 密码登录 |
| `/token` | 生成登录令牌（需已登录） |
| `/signin <token>` | 令牌登录 |
| `/account password <旧> <新>` | 改密码（令牌失效） |
| `/account invite [角色]` | 生成邀请码 |
| `/account open true\|false` | 开放注册 / 必须邀请码 |
| `/account stableuuid true\|false` | 固定 / 随机 UUID |
| `/account priority true\|false` | 正版优先顶号 |
| `/account role <账户> <角色>` | 设置角色 |
| `/wsm model <玩家> <模型>` | 绑定 YSM 模型 |
| `/wsm status` | 查看状态 |

## 安全说明

离线模式下客户端 UUID 可伪造，“正版优先”只是启发式，不是安全边界。需要可信身份请使用正版验证或代理层认证。
