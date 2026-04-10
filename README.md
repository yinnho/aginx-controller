# Aginx Controller

> Aginx 的 Android 客户端 — 通过手机访问和管理你的 Agent。

## 简介

Aginx Controller 是 [Aginx](https://github.com/yinnho/aginx) 项目的 Android 客户端应用。它让你在手机上直接与远程 Agent 对话、浏览服务器文件、管理多个 Aginx 实例。

核心协议层基于 [aginxium](https://github.com/yinnho/aginxium)（Rust 引擎），通过 UniFFI 编译为原生 `.so` 库，在 Android 上通过 JNA 调用，无需 WebSocket 或 HTTP 中间层。

## 功能

- **多服务器管理** — 添加、切换、删除多个 Aginx 实例
- **Agent 对话** — 与注册在 Aginx 上的任何 Agent 实时对话（支持流式输出）
- **对话历史** — 查看、恢复、删除历史对话
- **文件浏览** — 浏览服务器端目录，下载文件到手机
- **设备绑定** — 通过配对码安全绑定设备
- **权限响应** — 处理 Agent 发起的权限请求（如文件写入）
- **自动重连** — 网络断开后指数退避自动重连
- **Markdown 渲染** — Agent 回复支持代码块、表格、链接等

## 架构

```
┌──────────────────────────────────────────────┐
│                  Android App                  │
│                                              │
│  ┌──────────┐  ┌───────────┐  ┌───────────┐ │
│  │   UI     │  │ ViewModel │  │   Room    │ │
│  │ Compose  │──│  (状态)    │──│  (本地DB) │ │
│  └──────────┘  └─────┬─────┘  └───────────┘ │
│                      │                        │
│              ┌───────┴────────┐               │
│              │ AginxiumAdapter│               │
│              └───────┬────────┘               │
│                      │ JNA                     │
├──────────────────────┼────────────────────────┤
│              ┌───────┴────────┐               │
│              │ libaginxium.so │  Rust 引擎     │
│              └───────────────┘               │
└──────────────────────────────────────────────┘
                       │
                   TCP / TLS
                       │
              ┌────────┴────────┐
              │  Aginx Server   │
              │  (agent 宿主)    │
              └─────────────────┘
```

### 技术栈

| 层 | 技术 |
|----|------|
| UI | Jetpack Compose + Material 3 |
| 状态管理 | ViewModel + StateFlow |
| 本地存储 | Room (SQLite) |
| 网络引擎 | aginxium (Rust, via UniFFI) |
| FFI | JNA (libaginxium.so) |
| Markdown | CommonMark (commonmark-java) |
| 导航 | Navigation Compose |

## 项目结构

```
app/src/main/java/net/aginx/controller/
├── App.kt                     # Application
├── MainActivity.kt            # 入口 Activity
├── client/
│   ├── AginxiumAdapter.kt     # Rust FFI 适配层
│   └── ClientModels.kt        # 客户端数据模型
├── data/model/
│   ├── Models.kt              # 数据模型 (Aginx, ACP 事件类型)
│   └── AcpModels.kt           # ACP 协议模型
├── db/
│   ├── AppDatabase.kt         # Room 数据库
│   ├── dao/Dao.kt             # DAO
│   └── entities/Entities.kt   # 数据库实体
└── ui/
    ├── MainViewModel.kt       # 全局 ViewModel
    ├── navigation/NavGraph.kt # 导航图
    ├── home/HomeScreen.kt     # 首页 (服务器列表)
    ├── add/AddAginxScreen.kt  # 添加服务器
    ├── agents/
    │   ├── AgentListScreen.kt      # Agent 列表
    │   └── ConversationListScreen.kt # 对话列表
    ├── chat/
    │   ├── ChatScreen.kt      # 聊天界面
    │   └── ChatMarkdown.kt    # Markdown 渲染
    ├── common/
    │   ├── DirectoryBrowser.kt # 目录选择器
    │   ├── FileBrowser.kt     # 文件浏览器
    │   └── Utils.kt           # 工具函数
    └── theme/                 # 主题
        ├── Theme.kt
        └── Type.kt
```

## 页面导航

```
Home (服务器列表)
  ├── AddAginx (添加服务器 + 配对)
  └── AgentList (选中服务器后的 Agent 列表)
       └── ConversationList (选中 Agent 后的对话列表)
            ├── Chat (恢复历史对话)
            └── Chat (新对话)
```

## 编译

### 前置条件

- Android Studio (Iguana 或更新版本)
- Android SDK 34
- JDK 17
- Gradle 8.12

### 编译步骤

1. 克隆仓库：
   ```bash
   git clone https://github.com/yinnho/aginx-controller.git
   cd aginx-controller
   ```

2. 用 Android Studio 打开项目

3. 编译运行

> `libaginxium.so` 已预编译在 `app/src/main/jniLibs/arm64-v8a/` 中，无需额外构建 Rust 部分。
> 如需重新编译 Rust 引擎，参见 [aginxium](https://github.com/yinnho/aginxium) 项目。

## 使用流程

1. **添加服务器** — 输入 Aginx 地址（如 `agent://abc123.relay.aginx.net`），输入配对码绑定
2. **选择 Agent** — 连接后自动获取服务器上的 Agent 列表
3. **开始对话** — 选择一个 Agent，创建新对话或恢复历史对话
4. **实时交互** — 发送消息，流式接收回复，处理权限请求

## 相关项目

| 项目 | 说明 |
|------|------|
| [aginx](https://github.com/yinnho/aginx) | Agent 网关服务器 (Rust) |
| [aginxium](https://github.com/yinnho/aginxium) | 客户端引擎 (Rust) |
| aginx-relay | 中继服务 (Rust) |
| aginx-api | 云端 API (Rust/Axum) |

## License

MIT
