# wxnotif —— 微信通知实时记录

监听微信通知，**通知一出现就立刻**写一行到手机文件；按天打包 zip，**保留 7 天**，超期自动删除。

## 输出结构

```
/storage/emulated/0/Android/data/com.example.wxnotif/files/WeChatMonitor/
├── wx_2026-10-28.log      ← 当天，实时追加
├── wx_2026-10-27.zip      ← 隔天自动压缩，原 .log 删除
├── wx_2026-10-26.zip
└── …（超过 7 天的自动删掉）
```

> 这是 **App 专属外部目录**，不需要「所有文件访问」，普通文件管理器也不容易直接翻到。

日志行格式（TSV，UTF-8）：

```
2026-10-28 23:12:05	张三	你好
2026-10-28 23:12:30	家庭群	李四: 在吗
```

## 原理

`NotificationListenerService`：系统在通知发布的那一刻回调 `onNotificationPosted`，可拿到 `EXTRA_TITLE` / `EXTRA_TEXT` / `EXTRA_BIG_TEXT`。

- 每次通知追加当天 `.log`；
- 跨天（或每 6 小时兜底）跑一次 `maintain()`：把历史 `.log` 压成同名 `.zip` 并删原文件，再删除超过 7 天的 `.zip`；
- 全程不需要 root，不 hook 微信。

## 存储权限：不需要

日志写在 **App 专属外部目录**：

```
/storage/emulated/0/Android/data/com.example.wxnotif/files/WeChatMonitor/
```

- 不需要 `MANAGE_EXTERNAL_STORAGE`，也不需要任何存储权限；
- 卸载 App 时系统会一起清掉；
- 通过 adb / root 可以访问，普通文件管理器不容易直接看到。

## 构建

### A. Android Studio
打开 `wxnotif` 目录 → Build → Build APK。

### B. GitHub Actions
新建仓库推上去，加 `.github/workflows/build.yml`：

```yaml
name: Build APK
on:
  workflow_dispatch:
  push:
    paths: ['.github/workflows/build.yml']
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 17 }
      - uses: gradle/actions/setup-gradle@v5
        with:
          gradle-version: '8.9'
      - run: gradle assembleDebug
      - uses: actions/upload-artifact@v4
        with:
          name: wxnotif
          path: app/build/outputs/apk/debug/*.apk
```

> 工程里**没有** `gradlew`（二进制文件没随源码给），所以用 `gradle-version` 让 action 下载 Gradle，然后直接 `gradle assembleDebug`（AGP 8.7 需要 Gradle 8.9）。
> 本机有 Gradle 的话，也可以先 `gradle wrapper --gradle-version 8.9` 生成 wrapper 再提交，之后用 `./gradlew assembleDebug`。

## 安装与一键配置（App 内完成）

1. `adb install -r app-debug.apk`
2. 打开 App，点 **① 一键 root 授权**（第一次会弹 root 授权框，允许即可）：
   - 通知使用权（`cmd notification allow_listener`）
   - 电池白名单 / 后台运行 / 活跃分组
3. 点 **③ 启动保活服务**（常驻通知 + 开机自启）。

> 没有 root 时用 adb 兜底：
> ```bash
> adb shell cmd notification allow_listener com.example.wxnotif/.WxListener
> ```

## 保活机制

App 自带三层保活：

| 层 | 做法 |
|---|---|
| 前台服务 | `KeepAliveService` 常驻通知（`IMPORTANCE_MIN`，不吵） |
| 开机自启 | `BootReceiver` 监听 `BOOT_COMPLETED` |
| 定时重绑 | 每 60 秒 `requestRebind`，防止通知监听掉线 |

root 授权时还会自动执行：`dumpsys deviceidle whitelist +pkg`、`RUN_IN_BACKGROUND`、`am set-standby-bucket active`。

**MIUI 额外手动项**（系统不认 adb，只能手动点）：
- App 内点 **④ 打开 MIUI 自启动管理**，允许 WX Notif Logger；
- 设置 → 应用设置 → WX Notif Logger → 省电策略 = 无限制；
- 微信本身也要 **自启动 + 无限制**，否则后台不产生通知，自然没得记。

## 微信侧设置

微信 → 我 → 设置 → 消息通知 → **打开「通知显示消息详情」**，否则内容只有「你收到一条消息」。

## 拉取 / 查看

```bash
adb pull /storage/emulated/0/Android/data/com.example.wxnotif/files/WeChatMonitor/ ./wxlogs/
```

在线看最新日志（手机本地）：

```bash
tail -f /storage/emulated/0/Android/data/com.example.wxnotif/files/WeChatMonitor/wx_$(date +%F).log
```

## 保留策略（7 天）

`maintain()` 的逻辑：

1. 非当天的 `wx_*.log` → 压成 `wx_*.zip`，成功后删除原 `.log`；
2. 文件名日期早于「今天 - 7 天」的 `wx_*.zip` → 删除。

想改成 14 天：改 `WxListener.java` 里的 `KEEP_DAYS = 14`。

## 局限（通知类方案的通病）

1. 只能拿到**通知里显示的文字**；
2. 快速连发 / 群聊时通知会合并，可能只剩“N 条新消息”，会漏；
3. 长消息可能被系统截断；
4. 要**完整每条消息**，只能 hook 微信（LSPosed），有封号风险。

## 文件结构

```
wxnotif/
├── settings.gradle.kts
├── build.gradle.kts
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/example/wxnotif/
│           ├── WxListener.java       # 通知监听 + 写日志 + 打包 + 7 天清理
│           ├── MainActivity.java     # 一键授权 / 保活控制台
│           ├── KeepAliveService.java # 前台服务保活 + 定时重绑
│           ├── BootReceiver.java     # 开机自启
│           └── RootSetup.java        # root 批量授权
└── README.md
```
