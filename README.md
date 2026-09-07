# InGameIME

InGameIME 是 Minecraft 1.7.10 Forge 的客户端 Rime 输入法前端，可在游戏输入框中使用 Rime 输入中文，并在输入框附近显示预编辑文本和候选项。

## 功能

- 支持 Rime 方案、候选选择、翻页和方案切换。
- 支持中英文模式切换，可配置打开输入框时使用的模式。
- 可显示当前输入模式、方案切换提示和候选注释。
- 已接入原版输入框，以及 NotEnoughItems、Applied Energistics 2、ModularUI、ModularUI 2、BiblioCraft 和 ClipboardAnywhere 的输入框。
- 可为 Rime Ice 的全拼和小鹤双拼方案生成游戏物品名称词库。
- 仅需安装在客户端；输入法不可用时保留原版输入行为。

## 运行要求

- Minecraft `1.7.10`
- Minecraft Forge `10.13.4.1614`
- [JNA `5.14.0`](https://repo1.maven.org/maven2/net/java/dev/jna/jna/5.14.0/)
- [librime](https://github.com/rime/librime) 及其原生依赖
- 可用的 Rime 共享数据、用户数据和输入方案

librime 必须与运行 Minecraft 的 JVM 架构一致。InGameIME 的发布 JAR 不包含 JNA、librime、输入方案或词典。

## 安装

下面的“游戏实例目录”是 `mods`、`config` 等目录所在的位置。

### 1. JNA

- **直接下载：**[`jna-5.14.0.jar`](https://repo1.maven.org/maven2/net/java/dev/jna/jna/5.14.0/jna-5.14.0.jar)
- **文件列表和校验文件：**[Maven Central](https://repo1.maven.org/maven2/net/java/dev/jna/jna/5.14.0/)
- **放置位置：**`<游戏实例目录>/mods/jna-5.14.0.jar`

只需要 `jna-5.14.0.jar`。名称中带 `sources` 或 `javadoc` 的 JAR、Android 使用的 AAR 和 `jna-platform` 都不能替代它。

### 2. librime

InGameIME 当前使用 librime `1.17.0` 验证。下载的原生库必须与**启动 Minecraft 的 JVM 架构**一致，而不是只看 Windows 本身是 64 位还是 32 位。

#### Windows

从下表选择一个主包：

| JVM 架构 | 下载 | 从压缩包复制 | 放置位置 |
|---|---|---|---|
| 64 位 | [`rime-33e7814-Windows-msvc-x64.7z`](https://github.com/rime/librime/releases/download/1.17.0/rime-33e7814-Windows-msvc-x64.7z) | `dist/lib/rime.dll` | `<游戏实例目录>/ingameime/native/rime.dll` |
| 32 位 | [`rime-33e7814-Windows-msvc-x86.7z`](https://github.com/rime/librime/releases/download/1.17.0/rime-33e7814-Windows-msvc-x86.7z) | `dist/lib/rime.dll` | `<游戏实例目录>/ingameime/native/rime.dll` |

大多数现代启动器使用 64 位 JVM，应选择 x64 包。这里需要的是文件名以 `rime-` 开头的主包，不是用于开发和链接的 `rime-deps-` 包。

#### macOS

- **下载：**[`rime-33e7814-macOS-universal.tar.bz2`](https://github.com/rime/librime/releases/download/1.17.0/rime-33e7814-macOS-universal.tar.bz2)
- **放置位置：**将压缩包中 `dist/lib` 下的动态库复制到 `<游戏实例目录>/ingameime/native/`，保留 `rime-plugins` 子目录；主库最终路径必须是 `ingameime/native/librime.dylib`。

#### Linux

librime 1.17.0 的官方发布页没有提供 Linux 预编译包。请通过发行版软件源安装 librime，或按照 [librime 官方构建说明](https://github.com/rime/librime#build-and-install-on-linux)编译安装；可在 [Repology](https://repology.org/project/librime/versions) 查询各发行版的软件包。

安装后，将 `nativeLibraryDirectory` 设置为直接包含 `librime.so` 的目录。也可以把 `librime.so`、它指向的版本化库文件和所需插件一起复制到 `<游戏实例目录>/ingameime/native/`。

其他 librime 版本和发行文件可从 [librime Releases](https://github.com/rime/librime/releases) 获取。使用其他版本时，主库名称仍必须是 Windows 的 `rime.dll`、Linux 的 `librime.so` 或 macOS 的 `librime.dylib`。

### 3. Rime 输入方案和词典

推荐使用 Rime Ice，它同时提供全拼、小鹤双拼、基础配置和词典，也是游戏词库功能支持的方案。

- **直接下载：**[Rime Ice `full.zip`](https://github.com/iDvel/rime-ice/releases/latest/download/full.zip)
- **发布页：**[Rime Ice Releases](https://github.com/iDvel/rime-ice/releases)
- **放置位置：**将 `full.zip` 内的**全部内容**直接解压到 `<游戏实例目录>/ingameime/user/`。

解压后不应多出一层 `full` 或 `rime-ice` 目录。以下文件应能直接在 `ingameime/user` 中找到：

```text
ingameime/user/default.yaml
ingameime/user/rime_ice.schema.yaml
ingameime/user/double_pinyin_flypy.schema.yaml
ingameime/user/rime_ice.dict.yaml
```

新实例中的 `ingameime/shared` 可以保持为空。更新已经使用过的 Rime 用户目录前，请先备份；Rime Ice 官方建议全量安装时清空旧配置后再复制新文件。

Rime Ice 需要 Lua 模块。使用上述 librime Windows 1.17.0 主包时，在 InGameIME 配置中填写 `requiredModules=lua`；如果另外安装了语法模型，再加入 `octagram`。其他输入方案可从 [Rime 配置仓库索引](https://github.com/rime/home/wiki/RimeWithSchemata) 获取，并将方案文件及其依赖一起放入 `ingameime/user`。

### 4. 检查目录并启动

以 Windows 64 位和 Rime Ice 为例，最终目录应类似：

```text
<游戏实例目录>/
├─ mods/
│  ├─ ingameime-<版本>.jar
│  └─ jna-5.14.0.jar
└─ ingameime/
   ├─ native/
   │  └─ rime.dll
   ├─ shared/
   └─ user/
      ├─ default.yaml
      ├─ rime_ice.schema.yaml
      ├─ double_pinyin_flypy.schema.yaml
      ├─ rime_ice.dict.yaml
      ├─ lua/
      └─ opencc/
```

启动游戏后，在模组列表中打开 `InGameIME` -> `Config`。使用上面的实例内目录时，三个目录设置均保持为空，并设置：

```properties
general {
    B:enabled=true
    B:autoDetectSystemData=false
    S:schemaId=rime_ice
    S:requiredModules=lua
}
```

保存后重启客户端，在配置界面的“状态”页确认状态为“已启用”。需要使用小鹤双拼时，将 `schemaId` 改为 `double_pinyin_flypy` 后再次重启。

如果已经有可用的 Rime 用户目录，也可以在配置界面中填写现有路径，不必把数据复制到游戏实例目录。

## 配置

常用设置可在 Minecraft 的模组列表中打开 `InGameIME` -> `Config` 修改。配置文件位于 `config/ingameime.cfg`。

| 设置 | 说明 |
|---|---|
| `enabled` | 启用或停用 InGameIME。 |
| `modeSwitchKey` | 中英文切换方式：`shift`、`left_shift`、`ctrl_shift` 或 `disabled`。 |
| `openInputMode` | 打开输入框时沿用上次模式，或固定为中文、英文。可选值为 `remember`、`chinese`、`english`。 |
| `showModeIndicator` | 显示中英文模式提示。 |
| `modeNoticeMillis` | 模式提示显示时间，范围为 1000 至 10000 毫秒。 |
| `showSchemaNotice` | 切换方案后显示当前方案名称。 |
| `showCandidateComments` | 显示 Rime 返回的候选注释。 |
| `schemaId` | 启动时选择的 Rime 方案 ID；留空使用 Rime 默认方案。 |
| `requiredModules` | 当前方案需要的 librime 模块，使用逗号分隔，例如 `lua,octagram`。 |
| `nativeLibraryDirectory` | librime 主库所在目录；留空使用 `ingameime/native`。 |
| `sharedDataDirectory` | Rime 共享数据目录；留空使用 `ingameime/shared`。 |
| `userDataDirectory` | Rime 用户数据目录；留空使用 `ingameime/user`。 |
| `autoDetectSystemData` | 用户数据目录留空时，尝试使用已安装桌面输入法的 Rime 用户目录。 |

运行开关、数据目录、方案和模块设置在重启客户端后生效。复用桌面输入法的用户目录前，建议先备份其中的数据。

## 使用

打开受支持的输入框后即可按当前 Rime 方案输入。预编辑文本和候选项会显示在输入框附近，选词、翻页和提交按键由所用 Rime 方案决定。

默认短按任一 Shift 可切换中文和英文模式；长按 Shift 或与其他按键组合时保留原有按键行为。快捷键可在配置界面的“输入”页修改。

方案切换快捷键来自 Rime 配置。常见配置使用 `F4` 或 Ctrl + 反引号键打开方案菜单；实际按键以用户数据中的 `switcher` 和 `key_binder` 配置为准。

## 游戏词库

游戏词库功能面向 [Rime Ice](https://github.com/iDvel/rime-ice) 的 `rime_ice` 和 `double_pinyin_flypy` 方案。对应方案文件需已安装在当前 Rime 用户目录中。

在配置界面的“游戏词库”页点击“生成游戏词库”，InGameIME 会扫描已注册物品的中文名称，并生成全拼和小鹤双拼词库。生成成功后会自动重新加载当前方案，使新词库立即生效；界面提示无法热重载时，请重启客户端。

## 故障排查

- 在配置界面的“状态”页查看运行状态、当前方案和停用原因。
- 检查 `logs/latest.log` 中带有 `ingameime` 的日志，确认 JNA、librime、原生依赖、数据目录和方案均可用。
- 遇到原生库加载失败时，确认 librime 与 JVM 位数一致，并检查它依赖的动态库是否可被操作系统找到。
- 方案依赖 Lua、Octagram 等插件时，先安装对应 librime 插件，再将模块名写入 `requiredModules`。

更多 Rime 配置说明见 [Rime 官方文档](https://rime.im/docs/) 和 [Rime 配置仓库索引](https://github.com/rime/home/wiki/RimeWithSchemata)。

## 构建

```powershell
.\gradlew.bat build
```

构建产物位于 `build/libs`。

## 许可证

InGameIME 由 [liansishen](https://github.com/liansishen) 维护，使用 [MIT License](LICENSE) 发布。
