# InGameIME

InGameIME 是 Minecraft 1.7.10 Forge 客户端的 librime 前端。它在原版聊天框中处理 Rime 按键、提交文本，并显示预编辑文本和候选项。

- 作者：[liansishen](https://github.com/liansishen)
- 仓库：[github.com/liansishen/ingameime](https://github.com/liansishen/ingameime)
- 许可证：[MIT](LICENSE)

发布产物只有一个模组 JAR。它不会捆绑、下载、解压或更新 JNA、librime、原生依赖、插件、输入方案、词典或 Weasel。

## 当前范围

- 仅接入原版 `GuiChat` 聊天框。
- 使用用户安装的 JNA 和 librime，不调用 Weasel、Fcitx5 或 IBus 的进程接口。
- 支持 librime 可部署的通用 Rime 方案；`schemaId` 为空时使用 Rime 默认方案。
- 可按配置检查方案必需的 librime 模块，例如 `lua` 或 `octagram`。
- 任一外部依赖、目录、部署、模块、方案或 session 检查失败时完全旁路，原版输入保持不变。

## 手动安装

1. 安装 Minecraft 1.7.10 Forge `10.13.4.1614`，把 InGameIME JAR 放入客户端的 `mods` 目录。
2. 从 [JNA 官方仓库](https://github.com/java-native-access/jna) 或 [Maven Central](https://repo1.maven.org/maven2/net/java/dev/jna/jna/5.14.0/) 获取 `jna-5.14.0.jar`，使它位于游戏运行时 classpath。Forge 1.7.10 客户端通常可以从 `mods` 目录加载该 JAR。
3. 从 [librime 官方仓库](https://github.com/rime/librime) 或其 [Releases](https://github.com/rime/librime/releases) 安装与 JVM 位数一致的 librime，并保留它需要的原生动态库。不要把这些文件放进 InGameIME JAR。
4. 准备一个已经存在的 Rime 用户数据目录。最基础的验证可以使用 librime 官方 [`data/minimal`](https://github.com/rime/librime/tree/master/data/minimal) 中不依赖 Lua 的 `luna_pinyin` 数据。
5. 首次启动一次游戏以生成 `config/ingameime.cfg`，退出后填写下面的路径并重新启动。

Windows 示例使用正斜杠，避免配置文件中的反斜杠转义：

```properties
general {
    B:autoDetectSystemData=false
    B:enabled=true
    S:nativeLibraryDirectory=C:/Rime/bin
    S:requiredModules=
    S:schemaId=luna_pinyin
    S:sharedDataDirectory=C:/Rime/data
    S:userDataDirectory=C:/Users/you/AppData/Roaming/Rime
}
```

`nativeLibraryDirectory` 必须直接包含当前平台的主库：

- Windows：`rime.dll`
- Linux：`librime.so`
- macOS：`librime.dylib`

该目录中的其他动态库仍须能被操作系统的动态加载器找到。Windows 通常把依赖 DLL 放在同一目录；Linux 和 macOS 应按发行包要求配置系统库搜索路径。

## 配置

| 键 | 含义 |
|---|---|
| `enabled` | 总开关。为 `false` 时不加载 JNA 或 librime。 |
| `nativeLibraryDirectory` | 包含 librime 主库和其原生依赖的既有目录。必填。 |
| `userDataDirectory` | 既有 Rime 用户数据目录。留空时可自动检测。 |
| `sharedDataDirectory` | 既有 Rime 共享数据目录。留空时使用选中的用户数据目录。 |
| `autoDetectSystemData` | `userDataDirectory` 为空时，使用第一个检测到的系统 Rime 目录。 |
| `schemaId` | 启动后选择的方案 ID。留空使用 Rime 当前默认方案。 |
| `requiredModules` | 逗号分隔的必需模块名。留空表示方案不要求额外插件。 |

自动检测只接受已经存在的目录，不会创建目录。顺序如下：

- Windows：`%APPDATA%/Rime`、`%APPDATA%/Moqi/Rime`
- macOS：`~/Library/Rime`
- Linux：`$XDG_DATA_HOME/fcitx5/rime`、`$XDG_CONFIG_HOME/ibus/rime`、Fcitx5 Flatpak 数据目录

显式配置的目录始终优先。共享正在使用的系统 Rime 用户目录可能产生部署文件、用户词典写入和数据库锁冲突；需要隔离时，应手动复制一份完整数据目录，并在没有其他 Rime 前端使用它时完成部署。

## 方案与插件

基础方案不要求 Lua。白霜和其他复杂方案只是可选配置，不是 InGameIME 的实现或验证依赖。

### 方案热切换

方案热键来自用户数据中的 Rime `switcher` 和 `key_binder` 配置，不由模组硬编码。官方最小配置支持：

- `F4` 或 `Control+grave` 打开方案菜单；
- 方向键和 Enter，或候选数字键完成选择；
- `Ctrl+Shift+1` 直接切换到下一个方案。

切换成功后，聊天框上方会短暂显示实际生效的方案名称。Rime switcher 使用的内部 `.default` 状态不会被当作用户方案。组合状态下的 Rime 控制键优先交给 librime；没有预编辑或候选时，原版 `Ctrl+A/C/V/X` 行为保持不变。

当某个方案确实依赖插件时，在 `requiredModules` 中声明模块，例如：

```properties
S:requiredModules=lua,octagram
```

InGameIME 会通过 librime 的模块 API 检查每个声明项。缺少模块、方案未部署、方案选择失败或 session 状态异常都会禁用本次进程内的输入法接入，不会回退到部分功能。

插件和方案必须由用户从各自官方仓库安装，并遵守其许可证。常见来源包括：

- [librime-lua](https://github.com/hchunhui/librime-lua)
- [librime-octagram](https://github.com/lotem/librime-octagram)
- [Rime 配置仓库索引](https://github.com/rime/home/wiki/RimeWithSchemata)

## 旁路行为

InGameIME 只有 `UNINITIALIZED`、`ACTIVE` 和 `DISABLED` 三种进程内状态。初始化完整成功后才进入 `ACTIVE`。

在 JNA 缺失、主库缺失或架构不匹配、目录无效、librime 初始化或部署异常、必需模块缺失、方案不可用、session 创建失败等情况下，模组会记录一次禁用原因并保持 `DISABLED`：

- 不拦截任何按键；
- 不绘制候选窗；
- 不向聊天框写入文本；
- 不自行创建、下载或填充 Rime 数据目录。

librime 在成功初始化后的正常工作中可能部署配置并更新用户词典。这些写入由用户提供的 librime 和数据目录控制，因此使用现有系统目录前应先备份。

## 构建

```powershell
.\gradlew.bat build
```

JNA 使用 `compileOnly`，不会被打入输出 JAR，也不会作为发布依赖传递。构建产物位于 `build/libs`。
