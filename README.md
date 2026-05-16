# OpenCode Mobile App

OpenCode Mobile App 是一个原生 Android 客户端，用来通过 `opencode-mobile-agent` 连接并控制远程服务器上的 OpenCode 会话。

这个仓库只包含 Android App。服务端 Agent 在单独仓库：

```text
https://github.com/Risaly-Noroki-Dev-Club/opencode-mobile-agent
```

## 当前状态

项目已进入可用 MVP 阶段。当前 APK 支持：

* 连接 `opencode-mobile-agent`
* 读取 agent `/health` 状态
* 读取 `/projects` 项目列表
* 按项目读取 `/sessions` 历史会话
* 在选中项目目录下新建 OpenCode 会话
* 打开已有会话并继续聊天
* 通过 `/opencode/event` 接收 SSE 流式输出
* 权限审批弹窗
* 会话 Diff 查看器
* navi 命令/模板/模型选择面板
* 提供商和模型管理（添加/登录/选择）

当前仍是 debug 测试构建，尚未做正式发布、账号体系或多服务器管理。

## 技术栈

* Kotlin
* Jetpack Compose (Material 2)
* Material 2 — 从 Material 3 迁移而来，追求用户操作效率
* Android 12+ Dynamic Color（通过桥接层映射到 M2）
* OkHttp HTTP/SSE
* Kotlin Serialization
* DataStore
* Navigation Compose

## UI 设计

App 使用 Material Design 2（MD2）作为设计语言，主要考量：

* **用户操作效率**：MD2 更紧凑的控件间距和布局提高了屏幕信息密度
* **熟悉的交互模式**：Bottom sheet 使用经典的 `ModalBottomSheetLayout` 而非 M3 的 `ModalBottomSheet`
* **平台一致性**：使用 Roboto 字体，与 Android 系统字体保持一致
* **Dynamic Color 保留**：Android 12+ 设备仍可从壁纸提取主题色，通过桥接层映射到 M2 颜色槽

## 本地化

当前内置语言：

* English
* 简体中文

如果手机系统语言是简体中文，App 会显示中文界面；其他语言默认显示英文。

## 计划功能

* 多服务器管理
* Session 搜索
* Session 删除/重命名
* Release 签名和发布流程
* 消息复制/分享
* 离线缓存

## 构建

需要 Android SDK。当前工程已包含 Gradle wrapper。

```bash
./gradlew :app:assembleDebug
```

Debug APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 架构

```
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

App 通过 `opencode-mobile-agent` 的 HTTP + SSE API 与远程 OpenCode 交互。所有会话状态管理由 agent 负责，App 端仅做 Compose 状态保持。

## 注意

当前 APK 是 debug 构建，只适合测试安装，不适合作为正式发布版本。
