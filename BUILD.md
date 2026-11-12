# 构建与冒烟测试

M1 的第一小步：**先证明工具链能编译、模组能被服务器加载、指令能用**。
假玩家、YSM、皮肤都还没进来，这一步只验证地基。

## 1. 环境

- JDK 21：`D:\Program Files\Java\jdk-21.0.7`
- Gradle 8.10（项目里没有 wrapper，需要先生成或直接用 `gradle`）
- 网络：首次构建要下载 Fabric Loom、Minecraft 1.21.1、Yarn 映射、Fabric API

设好环境变量（当前 PowerShell 会话有效）：

```powershell
$env:JAVA_HOME = "D:\Program Files\Java\jdk-21.0.7"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version   # 应该显示 21.0.7
```

## 2. 构建

### 方式 A：已有 Gradle

```powershell
gradle wrapper --gradle-version 8.10
.\gradlew build
```

### 方式 B：没有 Gradle

1. 下载 <https://services.gradle.org/distributions/gradle-8.10-bin.zip>；
2. 解压到例如 `D:\tools\gradle-8.10`；
3. 执行：

```powershell
D:\tools\gradle-8.10\bin\gradle.bat build
```

### 方式 C：用 IntelliJ IDEA

用 IDEA 打开项目目录，它会识别 `build.gradle` 并自动下载 Gradle；在 Gradle 面板点 `build`。

首次构建会下载几百 MB，10–20 分钟属正常。

## 3. 产物

```
build\libs\craftfriend-0.1.0.jar
```

## 4. 部署冒烟

1. 准备 Fabric 1.21.1 服务端（Loader 0.16.14，Java 21）；
2. `mods/` 放入：
   - `craftfriend-0.1.0.jar`
   - `fabric-api-0.116.18+1.21.1.jar`
3. 启动服务器，日志里应出现：

```
[CraftFriend] 模组已加载，等待服务器启动
[CraftFriend] 服务器已启动，居民系统待命
```

4. 进游戏执行：

```
/resident ping
```

应回复 `pong — CraftFriend 在线`。

## 5. 常见错误

| 现象 | 原因 | 处理 |
|---|---|---|
| `Unsupported class file major version` | 用了 JDK 17 或更低 | 确认 `JAVA_HOME` 指向 jdk-21.0.7 |
| `Could not resolve net.fabricmc:fabric-loom` | 插件仓库没配好 / 网络 | 检查 `settings.gradle` 的 `pluginManagement` |
| `fabric.mod.json` 里出现 `${version}` | 资源展开失败 | 清理后重跑：`gradle clean build` |
| 服务器报 `requires fabric-api` | 没放 Fabric API | 下载对应 1.21.1 版本 |
| 构建卡在 `Downloading Minecraft` | 网络慢 | 重试；或配代理 |

## 6. 这一步的验收

- [ ] `gradle build` 成功，产出 jar
- [ ] 服务器带模组正常启动，无报错
- [ ] `/resident ping` 返回 pong
- [ ] 普通玩家（非 OP）也能执行

四项都过，再进 M1 的下一个 spike：用 Carpet 生成一个假玩家。
