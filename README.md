# OpenCode Mobile App

OpenCode Mobile App 是一个原生 Android 客户端，用来通过 `opencode-mobile-agent` 连接并控制远程服务器上的 OpenCode 会话。

这个仓库只包含 Android App。服务端 Agent 在单独仓库：

```text
https://github.com/Risaly-Noroki-Dev-Club/opencode-mobile-agent
```

## 当前状态

项目还处于早期测试阶段。当前 APK 主要用于验证：

- 原生 Android 工程结构
- Material 3 主题
- Android 12+ 动态取色
- 中文/英文资源切换
- 连接页 UI 骨架

实际连接、会话、权限审批、diff 查看等功能会在后续版本继续实现。

## 技术栈

- Kotlin
- Jetpack Compose
- Material 3
- Material 3 Expressive，后续在依赖稳定后逐步接入
- Android 12+ Dynamic Color
- OkHttp HTTP/WebSocket
- Kotlin Serialization
- DataStore
- Navigation Compose

## 本地化

当前内置语言：

- English
- 简体中文

如果手机系统语言是简体中文，App 会显示中文界面；其他语言默认显示英文。

## 计划页面

- 连接页
- Workspace 列表
- Session 列表
- 会话时间线
- 权限审批弹窗
- Diff 查看器

## 构建

需要 Android SDK。当前工程已包含 Gradle wrapper。

```bash
./gradlew :app:assembleDebug
```

Debug APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Agent 连接方式

App 后续会连接用户自己服务器上的 `opencode-mobile-agent`。

推荐部署形态：

```text
Android App
  |
  | https://your-server:2250
  v
opencode-mobile-agent
  |
  | http://127.0.0.1:4096
  v
opencode serve
```

如果服务器只开放 `2250-2300` 端口，推荐让 Agent 监听 `2250`，OpenCode 自身继续只监听本机 `127.0.0.1:4096`。

## 注意

当前 APK 是 debug 构建，只适合测试安装，不适合作为正式发布版本。
