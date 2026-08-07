# Ancode

**Android 原生 vibe coding Agent** —— 类 Claude Code / OpenCode 的移动端 AI 编程助手。

Ancode 在手机上内置一个 **proot Ubuntu 24.04 (arm64)** 环境，配合 Kotlin 原生实现的 Agent 核心
（LLM 流式调用 + 工具循环 + Do List + 交互终端），让你随时随地用自然语言驱动 AI 写代码、跑命令、改文件。

```
┌─────────────────────────────────────────────┐
│  ANCODE  ·  🟢 Agent 思考中...               │
│                                             │
│  ▸ 用户：帮我用 Python 写一个端口扫描器      │
│  ▸ ANCODE                                  │
│    我先规划一下任务：                       │
│    [✓] 了解需求                             │
│    [✓] 编写 scanner.py                      │
│    [✓] 运行测试                             │
│  ┌─ terminal ──────────────────────────┐   │
│  │ $ python3 scanner.py --host 1.2.3.4 │   │
│  │ [exit=0] 端口 22/80/443 开放        │   │
│  └─────────────────────────────────────┘   │
│  ▍                                         │
│  [ 输入任务… ]                    [➤]      │
└─────────────────────────────────────────────┘
```

## ✨ 功能特性

| 功能 | 说明 |
|------|------|
| 🤖 **Agent 核心** | Kotlin 原生实现，参考 OpenCode build agent 设计：`system + 历史 + 工具结果 → 模型 → 执行工具 → 回填结果` 的自主循环 |
| 🐧 **内置 Linux 环境** | 首次运行自动下载 `ubuntu-base-24.04.3-base-arm64`（28MB，SHA256 校验，多镜像+手动导入兜底），通过 **proot**（免 root）运行 |
| 📟 **现代 TUI 风 UI** | Jetpack Compose 深色终端美学：等宽字体、ANSI 渲染、Markdown、工具调用卡片、流式光标 |
| 🛠️ **内置工具** | `terminal`（proot 内 bash）、`read_file` / `write_file` / `edit_file`、`glob`、`grep`、`todo` |
| ✅ **Do List（工具）** | Claude Code 风格待办清单作为 **agent 工具**（todo），进度实时以工具调用卡片展示在对话流中 |
| 💬 **会话管理** | 会话列表与对话合并为一个「聊天」页，点击会话标题即可切换；JSON 持久化 |
| 🔌 **多模型供应商** | 同时配置多个 OpenAI 兼容供应商（DeepSeek / 通义 / 智谱 / OpenAI / Ollama / Moonshot / SiliconFlow / 自定义），随时切换 |
| 📱 **交互终端** | 真实 PTY（JNI + posix_openpt）+ 基础 VT100 ANSI 渲染 + 彩色 PS1/高亮，可手动敲命令 |
| 🗂️ **工作区直通** | 项目文件写入应用私有目录 `files/projects`（guest 内 `/root/projects`），debug 版集成 MTDataFilesProvider，MT 管理器免 ROOT 直接浏览 |
| ⚙️ **MCP / Skills 预留** | `Tool` 接口即扩展点，下一里程碑接入 MCP 服务器与 Skills 系统 |

## 🏗️ 架构

```
┌────────────────────────────────────────────────────────┐
│                        UI (Compose)                    │
│  ChatScreen · TerminalScreen · TodoPanel · Sessions    │
│  Settings · Markdown 渲染 · ANSI 终端渲染器              │
└──────────────┬─────────────────────────────────────────┘
               │ StateFlow 事件流
┌──────────────▼─────────────────────────────────────────┐
│              AppViewModel（会话/状态/持久化）             │
└──────┬──────────────────────┬──────────────────────────┘
       │                      │
┌──────▼───────┐      ┌───────▼────────┐
│ AgentEngine  │      │  SessionStore  │
│ 工具循环/停止 │      │  AppSettings   │
└──┬───────┬───┘      └────────────────┘
   │       │
┌──▼───┐ ┌─▼──────────┐   ┌──────────────────────┐
│ LLM  │ │ ToolRegistry│   │  Linux 层             │
│Client│ │ terminal    │   │  RootfsManager(下载/  │
│ SSE  │ │ read/write  │   │   校验/解压)          │
│ 流式 │ │ edit/glob   │   │  ProotRunner(命令执行)│
└──────┘ │ grep/todo   │   │  Pty(JNI/PTY 交互)   │
         └─────────────┘   └──────────────────────┘
```

### 目录结构

```
app/src/main/
├── java/com/ancode/app/
│   ├── MainActivity.kt / AppViewModel.kt
│   ├── agent/        # AgentEngine（工具循环）、SystemPrompt（专用提示词）
│   ├── llm/          # LlmClient（OpenAI 兼容 SSE 流式）、ChatModels
│   ├── linux/        # RootfsManager、ProotRunner、Pty（JNI 桥）
│   ├── tools/        # Tool 接口 + terminal/read/write/edit/glob/grep/todo
│   ├── model/        # ChatMessage、ToolCall、Session、TodoItem
│   ├── session/      # SessionStore（JSON 持久化）
│   ├── settings/     # AppSettings（DataStore）
│   └── ui/           # screens + Markdown/AnsiTerminal 渲染器 + theme
├── cpp/              # pty_shim.c（posix_openpt/fork/exec JNI）
└── assets/linux/     # proot 二进制 + 依赖 .so（patchelf 修正路径）
```

## 🚀 构建（GitHub Actions 云端）

项目已配置 `.github/workflows/build.yml`，推送 `main` 分支自动构建 APK：

```bash
git clone https://github.com/xis3794/ancode.git
cd ancode
# 手动触发：GitHub Actions → Build APK → Run workflow
```

产物：`app/build/outputs/apk/debug/app-debug.apk`（artifact: `ancode-debug-apk`）

### 本地构建

```bash
# 需要 JDK 17 + Android SDK 35 + NDK 27 + CMake 3.22.1
./gradlew assembleDebug
```

> 说明：CI 为 x86_64，使用标准 AAPT2；`app/build.gradle.kts` 中的 aapt2 替换仅在
> aarch64 宿主（如 Operit proot 环境）生效，互不影响。

## 📱 使用

1. **安装 APK** → 打开 Ancode
2. **设置** → 添加/切换模型供应商（可同时配置多个）：
   - DeepSeek: `https://api.deepseek.com` · `deepseek-chat`
   - 通义: `https://dashscope.aliyuncs.com/compatible-mode/v1` · `qwen-plus`
   - Ollama: `http://<局域网IP>:11434/v1` · 任意已拉取模型
3. **设置 → Linux 环境** → 点击「安装环境」（下载 28MB rootfs + 解压，一次完成）
   - 若网络下载失败：手动下载 `ubuntu-base-24.04.3-base-arm64.tar.gz` 放到 `/sdcard/Download/`，重新点击安装即自动导入
4. 回到**聊天**页，直接描述任务：
   - 普通输入 → Agent 自主执行（规划 Do List → 读文件 → 写代码 → 跑命令）
   - `! <命令>` → 快速执行终端命令
   - 顶部会话标题可点击切换/新建会话
5. **终端**页 → 交互式 bash（完整 Ubuntu 环境，彩色 PS1 + ls/grep 高亮）
6. **Do List**：Agent 通过 `todo` 工具维护，进度以工具卡片实时展示在对话流中
7. **MT 管理器**（debug 版内置 MTDataFilesProvider）：添加本地存储 → 选中 Ancode → 免 ROOT 浏览 `files/projects`（AI 生成的项目文件）与 `files/linux`（rootfs）

## 🧠 专用系统提示词

`agent/SystemPrompt.kt` 内置为 vibe coding 场景调优的提示词，核心约束：

- 先理解再动手：优先用工具探索项目，不凭空猜测
- Do List 驱动：任务拆解为小步骤，逐步验证
- 小步实施：一次一个逻辑单元，改完即验证
- 报错闭环：定位 → 修复 → 复验，最多 3 次尝试
- 诚实输出：工具结果如实报告，禁止假装执行

## 🗺️ Roadmap

- [ ] **MCP 支持**（stdio/SSE 客户端，Tool 接口扩展点已就绪）
- [ ] **Skills 系统**（SKILL.md 加载与语义触发）
- [ ] Anthropic / Gemini 适配器
- [ ] 完整 VT100 仿真（vim/htop 友好）
- [ ] rootfs 内置常用工具（python3/node/git 预装包）
- [ ] 文件管理器 UI、代码高亮、Diff 视图

## 📄 License

MIT — 参考与致敬 [sst/opencode](https://github.com/sst/opencode)（架构设计）与 Termux（proot 移植）。
