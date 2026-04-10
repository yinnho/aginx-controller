# 手机端接手电脑端对话 — 修改计划

## 目标
App 通过 aginx 直接操作 Claude Agent，实现手机和电脑无缝衔接。
aginx 作为透明桥梁，不维护独立的会话管理。

---

## 一、服务端 (aginx)

### 1.1 简化 listConversations
- **改为**: 直接扫描 `~/.claude/projects/`，返回 Claude 原生 session ID
- **文件**: `src/agent/session.rs`, `src/acp/handler.rs`
- **状态**: [x] 已完成 — `scan_claude_sessions_direct()`

### 1.2 简化 getConversationMessages
- **改为**: 直接读 JSONL，支持 `limit` 参数（默认 10），过滤系统消息
- **文件**: `src/agent/session.rs`
- **状态**: [x] 已完成 — `read_claude_jsonl_messages_limited()`

### 1.3 简化 loadSession（恢复对话）
- **改为**: 接收 Claude session ID，启动 `claude --resume <session_id>`
- **文件**: `src/acp/handler.rs`
- **状态**: [x] 已完成 — `create_session_with_claude_id()`

### 1.4 简化 deleteConversation
- **改为**: 只删 JSONL 文件
- **文件**: `src/acp/handler.rs`, `src/agent/session.rs`
- **状态**: [x] 已完成 — `delete_claude_jsonl_by_session_id()`

### 1.5 清理废弃代码
- 删除 `~/.aginx/sessions/claude/` 相关的元数据管理代码
- 删除 deleted 标记机制
- **状态**: [x] 已完成
  - 移除 9 个废弃方法（find_agent_for_session, get_persisted_metadata, load_deleted_markers 等）
  - 移除未使用的 imports（HashSet）、to_api_messages 方法
  - cargo build --release 零警告通过

---

## 二、App 端 (aginx-controller)

### 2.1 ChatScreen 不走 Room DB，直接用 state
- **改为**: 用 `serverMessages` state 变量直接显示，每次打开都从服务端拉最后 10 条
- **文件**: `ChatScreen.kt`, `MainViewModel.kt`, `AgentClient.kt`
- **状态**: [x] 已完成
  - `serverMessages` state 替代 `dbMessages` (Room DB Flow)
  - `refreshMessages()` 从服务端拉取最后 10 条消息
  - `AgentClient.getConversationMessages()` 添加 `limit` 参数
  - `onSend` 不再保存到 Room DB，完成后刷新服务端消息
  - 新对话：`initServerSession()` → `sendMessageWithSession()`
  - 已有对话：`loadSession()` → `refreshMessages()` → 可发送

### 2.2 所有对话都可交互
- **改为**: 统一流程 — loadSession → 显示最后 10 条 → 可以发消息
- **文件**: `ChatScreen.kt`
- **状态**: [x] 已完成（与 2.1 合并实现）

### 2.3 清理 App 端废弃代码
- 删除 Room DB 保存逻辑
- **状态**: [x] 已完成
  - 移除 MainViewModel 中 12 个废弃方法（getConversationsForAgent, createConversation, saveMessage 等）
  - 移除未使用的 imports（Flow, ConversationEntity, MessageEntity, UUID）
  - 移除未使用的 DAO 字段（conversationDao, messageDao）

---

## 三、验证

### 3.1 编译测试
- [x] `cargo build --release` 通过
- [x] `./gradlew assembleDebug` 通过

### 3.2 功能测试
- [ ] App 对话列表显示 Claude CLI 的所有对话
- [ ] 打开对话能看到最近 10 条有效消息
- [ ] 能继续发消息并获得回复
- [ ] 删除对话后 JSONL 文件被删除
- [ ] 新建对话正常工作
