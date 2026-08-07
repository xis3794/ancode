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
        viewModelScope.launch {
            settings.settings.collect { _settings.value = it }
        }
        viewModelScope.launch {
            settings.activeSessionId.collect { id ->
                if (id != null) loadSession(id) else createSession()
            }
        }
        viewModelScope.launch { refreshSessionList() }
    }

    // ---- session management ----

    fun createSession() {
        viewModelScope.launch {
            val s = sessionStore.newSession("新会话 ${System.currentTimeMillis().toString().takeLast(4)}")
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
            settings.setActiveSession(id)
            refreshSessionList()
        }
    }

    fun newSessionFromId(id: String) = loadSession(id)

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
            val ok = rootfs.install()
            _statusText.value = if (ok) "Ubuntu 环境就绪" else "安装失败：${rootfs.state.value.error}"
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
        val isLocal = cfg.baseUrl.contains("10.0.2.2") || cfg.baseUrl.contains("localhost") || cfg.baseUrl.contains("127.0.0.1")
        if (cfg.apiKey.isBlank() && !isLocal) {
            appendMessage(ChatMessage(role = Role.ASSISTANT, content = "⚠️ 尚未配置 API Key。请前往「设置」填写 API Key（支持 DeepSeek / OpenAI / 通义 / 智谱 / Ollama 等 OpenAI 兼容接口）。"))
            persist()
            return
        }
        if (!rootfs.isReady()) {
            appendMessage(ChatMessage(role = Role.ASSISTANT, content = "⚠️ Ubuntu 环境尚未安装。请先到「设置 → Linux 环境」点击安装（首次需下载约 28MB rootfs）。"))
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
            engine.run(messages) { event ->
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
            // flush final streamed content into a message
            val finalText = _streamingText.value
            _streamingText.value = ""
            if (finalText.isNotBlank()) {
                appendMessage(ChatMessage(role = Role.ASSISTANT, content = finalText))
            }
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