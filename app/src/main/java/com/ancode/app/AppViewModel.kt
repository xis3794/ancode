package com.ancode.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ancode.app.agent.AgentEngine
import com.ancode.app.agent.SystemPrompt
import com.ancode.app.llm.LlmClient
import com.ancode.app.linux.ProotRunner
import com.ancode.app.linux.RootfsManager
import com.ancode.app.model.ChatMessage
import com.ancode.app.model.Role
import com.ancode.app.model.Session
import com.ancode.app.model.SessionSummary
import com.ancode.app.model.TodoItem
import com.ancode.app.session.SessionStore
import com.ancode.app.settings.AppSettings
import com.ancode.app.tools.EditTool
import com.ancode.app.tools.GlobTool
import com.ancode.app.tools.GrepTool
import com.ancode.app.tools.ReadTool
import com.ancode.app.tools.TerminalTool
import com.ancode.app.tools.TodoTool
import com.ancode.app.tools.ToolRegistry
import com.ancode.app.tools.WriteTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Central state holder: sessions, agent, tools, linux env, terminal. */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    // ---- services ----
    val settings = AppSettings(app)
    val sessionStore = SessionStore(app)
    val rootfs = RootfsManager(app)
    private val runner = ProotRunner(rootfs)
    val todoTool = TodoTool(onUpdate = { items -> _todos.value = items })

    // ---- UI state ----
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _todos = MutableStateFlow<List<TodoItem>>(emptyList())
    val todos: StateFlow<List<TodoItem>> = _todos.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    private val _statusText = MutableStateFlow("就绪")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _currentSession = MutableStateFlow<Session?>(null)
    val currentSession: StateFlow<Session?> = _currentSession.asStateFlow()

    private val _sessions = MutableStateFlow<List<SessionSummary>>(emptyList())
    val sessions: StateFlow<List<SessionSummary>> = _sessions.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings.Settings())
    val settingsFlow: StateFlow<AppSettings.Settings> = _settings.asStateFlow()

    private var agent: AgentEngine? = null
    private var runJob: Job? = null

    init {
        // restore rootfs status on cold start — otherwise the UI shows
        // "未安装" even though the rootfs is already on disk
        rootfs.refreshState()
        viewModelScope.launch { rootfs.restoreIfInstalled() }
        viewModelScope.launch {
            settings.settings.collect { _settings.value = it }
        }
        viewModelScope.launch {
            // seed counter from existing sessions so names never collide
            sessionCounter = sessionStore.list().size
            settings.activeSessionId.collect { id ->
                if (id != null) loadSession(id) else createSession()
            }
        }
        viewModelScope.launch { refreshSessionList() }
    }

    // ---- session management ----

    /** Monotonic counter so new sessions are named 新会话1, 新会话2, … (no flicker). */
    private var sessionCounter = 0

    fun createSession() {
        viewModelScope.launch {
            sessionCounter++
            val s = sessionStore.newSession("新会话 $sessionCounter")
            rootfs.setCurrentWorkspace(s.workspaceId)
            settings.setActiveSession(s.id)
        }
    }

    fun loadSession(id: String) {
        viewModelScope.launch {
            val s = sessionStore.load(id) ?: return@launch
            _currentSession.value = s
            _messages.value = s.messages
            todoTool.restore(s.todos)
            _todos.value = s.todos
            // switch this session's workspace before any tool runs
            rootfs.setCurrentWorkspace(s.workspaceId)
            settings.setActiveSession(id)
            refreshSessionList()
        }
    }

        fun newSessionFromId(id: String) = loadSession(id)

    /** Create a new session and immediately send the first user message (Home prompt). */
    fun newSessionAndSend(text: String) {
        if (_isRunning.value) return
        viewModelScope.launch {
            sessionCounter++
            val s = sessionStore.newSession("新会话 $sessionCounter")
            rootfs.setCurrentWorkspace(s.workspaceId)
            settings.setActiveSession(s.id)
            _currentSession.value = s
            _messages.value = emptyList()
            todoTool.restore(emptyList())
            _todos.value = emptyList()
            appendMessage(ChatMessage(role = Role.USER, content = text.trim()))
            persist()
            runAgent()
        }
    }

    fun deleteSession(id: String) {
        viewModelScope.launch {
            sessionStore.delete(id)
            if (_currentSession.value?.id == id) {
                settings.setActiveSession(null)
                createSession()
            }
            refreshSessionList()
        }
    }

    fun renameSession(id: String, title: String) {
        viewModelScope.launch {
            sessionStore.rename(id, title)
            refreshSessionList()
        }
    }

    private suspend fun refreshSessionList() {
        _sessions.value = sessionStore.list()
    }

    // ---- persist ----

    private suspend fun persist() {
        val s = _currentSession.value ?: return
        val updated = s.copy(
            messages = _messages.value,
            todos = _todos.value
        )
        sessionStore.save(updated)
        _currentSession.value = updated
        refreshSessionList()
    }

    // ---- linux env ----

    fun installRootfs() {
        viewModelScope.launch {
            _statusText.value = "正在安装 Ubuntu 环境..."
            runCatching { rootfs.install() }
                .onSuccess { ok ->
                    _statusText.value = if (ok) "Ubuntu 环境就绪" else "安装失败：${rootfs.state.value.error}"
                }
                .onFailure { e ->
                    _statusText.value = "安装异常：${e.message}"
                }
        }
    }

    /** Cancel an in-flight rootfs download / extraction. */
    fun cancelRootfsInstall() {
        rootfs.cancelInstall()
        _statusText.value = "已取消安装"
    }

    /** Import a user-picked rootfs tarball (SAF — no storage permission needed). */
    fun importRootfsFromUri(uri: android.net.Uri) {
        viewModelScope.launch {
            _statusText.value = "正在导入 rootfs..."
            runCatching { rootfs.importFromUri(uri) }
                .onSuccess { err ->
                    _statusText.value = err ?: "Ubuntu 环境就绪"
                }
                .onFailure { e ->
                    _statusText.value = "导入异常：${e.message}"
                }
        }
    }

    /** Probe + prepare projects dir; returns probe output for the UI. */
    suspend fun probeLinux(): String {
        if (!rootfs.isReady()) return "Linux 环境未安装"
        runner.ensureProjectsDir()
        return runner.probe()
    }

    // ---- agent ----

    fun sendUserMessage(text: String) {
        if (_isRunning.value) return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            appendMessage(ChatMessage(role = Role.USER, content = trimmed))
            persist()
            runAgent()
        }
    }

    fun stopAgent() {
        agent?.cancel()
    }

    private suspend fun runAgent() {
        if (_isRunning.value) return
        val cfg = settings.llmConfig()
        val hasProvider = _settings.value.providers.isNotEmpty()
        if (!hasProvider) {
            appendMessage(ChatMessage(role = Role.ASSISTANT, content = "尚未配置模型供应商。请前往「设置 → 模型供应商」点击 + 添加。"))
            persist()
            return
        }
        val isLocal = cfg.baseUrl.contains("10.0.2.2") || cfg.baseUrl.contains("localhost") || cfg.baseUrl.contains("127.0.0.1")
        if (cfg.apiKey.isBlank() && !isLocal) {
            appendMessage(ChatMessage(role = Role.ASSISTANT, content = "当前供应商尚未填写 API Key。请前往「设置 → 模型供应商」编辑并填入。"))
            persist()
            return
        }
        if (!rootfs.isReady()) {
            appendMessage(ChatMessage(role = Role.ASSISTANT, content = "Ubuntu 环境尚未安装。请先到「设置 → Linux 环境」点击安装（首次需下载约 28MB rootfs）。"))
            persist()
            return
        }

        val llm = LlmClient(cfg)
        val registry = ToolRegistry(
            listOf(
                TerminalTool(runner),
                ReadTool(rootfs),
                WriteTool(rootfs),
                EditTool(rootfs),
                GlobTool(rootfs),
                GrepTool(rootfs),
                todoTool
            )
        )
        val engine = AgentEngine(
            llm = llm,
            registry = registry,
            systemPrompt = SystemPrompt.build(cfg.model, _settings.value.workingDir)
        )
        agent = engine
        _isRunning.value = true
        _streamingText.value = ""
        _statusText.value = "Agent 思考中..."

        val messages = _messages.value.toMutableList()
        runJob = viewModelScope.launch {
            engine.run(
                messages = messages,
                onSync = { snapshot -> _messages.value = snapshot }
            ) { event ->
                when (event) {
                    is AgentEngine.Event.TurnStarted -> _statusText.value = "第 ${event.turn} 轮..."
                    is AgentEngine.Event.TextDelta -> _streamingText.value += event.text
                    is AgentEngine.Event.ToolCallStarted -> _statusText.value = "执行工具 ${event.name}..."
                    is AgentEngine.Event.ToolCallFinished -> _statusText.value = "工具 ${event.name} ${if (event.ok) "完成" else "失败"} (${event.durationMs}ms)"
                    is AgentEngine.Event.TurnFinished -> _streamingText.value = ""
                    is AgentEngine.Event.Finished -> _statusText.value = "完成：${event.reason}"
                    is AgentEngine.Event.Error -> _statusText.value = "错误：${event.message}"
                }
            }
            // engine already appended every assistant / tool message via onSync
            _messages.value = messages.toList()
            _streamingText.value = ""
            _isRunning.value = false
            persist()
            _statusText.value = "就绪"
        }
    }

    private suspend fun appendMessage(msg: ChatMessage) {
        _messages.value = _messages.value + msg
    }

    /** Update todo from UI (user toggles a checkbox). */
    fun toggleTodo(id: String) {
        viewModelScope.launch {
            todoTool.restore(_todos.value.map { if (it.id == id) it.copy(done = !it.done) else it })
            persist()
        }
    }

    /** Quick terminal command from the chat input (starts with "!"). */
    fun runQuickCommand(command: String) {
        if (_isRunning.value) return
        viewModelScope.launch {
            appendMessage(ChatMessage(role = Role.USER, content = "! $command"))
            val res = runner.execute(command, cwd = _settings.value.workingDir, timeoutMs = 120_000)
            val out = if (res.timedOut) "（超时）" else "exit=${res.exitCode}"
            appendMessage(ChatMessage(role = Role.ASSISTANT, content = "```\n$ ${command}\n${res.output}\n[$out]\n```"))
            persist()
        }
    }

    // ---- settings ----

    /** Upsert a provider profile (empty id = create new). */
    fun saveProvider(profile: com.ancode.app.settings.ProviderProfile, makeActive: Boolean = false) {
        viewModelScope.launch {
            settings.upsertProvider(profile, makeActive)
        }
    }

    fun deleteProvider(id: String) {
        viewModelScope.launch {
            settings.deleteProvider(id)
        }
    }

    fun setActiveProvider(id: String) {
        viewModelScope.launch {
            settings.setActiveProvider(id)
        }
    }

    fun saveWorkingDir(dir: String) {
        viewModelScope.launch {
            settings.update(workingDir = dir)
        }
    }

    fun clearSessionMessages() {
        viewModelScope.launch {
            _messages.value = emptyList()
            todoTool.restore(emptyList())
            persist()
        }
    }

    override fun onCleared() {
        runJob?.cancel()
        super.onCleared()
    }
}