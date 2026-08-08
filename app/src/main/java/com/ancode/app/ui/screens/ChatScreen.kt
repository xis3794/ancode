package com.ancode.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ancode.app.AppViewModel
import com.ancode.app.model.ChatMessage
import com.ancode.app.model.Role
import com.ancode.app.model.ToolCall
import com.ancode.app.ui.theme.Accent
import com.ancode.app.ui.theme.BgDeep
import com.ancode.app.ui.theme.BgElevated
import com.ancode.app.ui.theme.BorderDim
import com.ancode.app.ui.theme.Error
import com.ancode.app.ui.theme.Success
import com.ancode.app.ui.theme.TextMuted
import com.ancode.app.ui.theme.TextPrimary
import com.ancode.app.ui.theme.TextSecondary

/** OpenCode-style TUI chat tab: Home (session list) ↔ Session (stream + prompt). */
@Composable
fun ChatScreen(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val current = viewModel.currentSession.collectAsState().value
    val sessions by viewModel.sessions.collectAsState()
    val settings by viewModel.settingsFlow.collectAsState()
    var showSessions by remember { mutableStateOf(current == null) }

    LaunchedEffect(current?.id) { if (current != null) showSessions = false }

    val modelLabel = settings.providers.firstOrNull { it.id == settings.activeProviderId }?.name
        ?: settings.providers.firstOrNull()?.name ?: "未配置"

    if (showSessions || current == null) {
        HomeView(
            sessions = sessions,
            currentId = current?.id,
            onOpen = { id -> viewModel.loadSession(id); showSessions = false },
            onNew = { viewModel.createSession(); showSessions = false },
            onDelete = { viewModel.deleteSession(it) },
            modelLabel = modelLabel,
            modifier = modifier
        )
        return
    }

    SessionView(
        viewModel = viewModel,
        current = current,
        modelLabel = modelLabel,
        onBack = { showSessions = true },
        modifier = modifier
    )
}

// ─────────────────────────── Home ───────────────────────────

@Composable
private fun HomeView(
    sessions: List<com.ancode.app.model.SessionSummary>,
    currentId: String?,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onDelete: (String) -> Unit,
    modelLabel: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxSize().background(BgDeep).padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(36.dp))
        Text("Sessions", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text("model: $modelLabel", color = TextMuted, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        Spacer(Modifier.height(18.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .background(BgElevated, RoundedCornerShape(12.dp))
                .border(1.dp, BorderDim, RoundedCornerShape(12.dp))
                .clickable(onClick = onNew)
                .padding(horizontal = 14.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("+", color = Accent, fontFamily = FontFamily.Monospace, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("New session", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text("开始一个新的对话", color = TextMuted, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("RECENT", color = TextMuted, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        if (sessions.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                Text("暂无历史会话", color = TextMuted, fontSize = 13.sp)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(sessions, key = { it.id }) { s ->
                    SessionRow(s, s.id == currentId, onOpen = { onOpen(s.id) }, onDelete = { onDelete(s.id) })
                }
            }
        }
    }
}

@Composable
private fun SessionRow(
    summary: com.ancode.app.model.SessionSummary,
    active: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (active) Color(0xFF16233A) else BgElevated, RoundedCornerShape(10.dp))
            .border(1.dp, if (active) Accent.copy(alpha = 0.6f) else BorderDim, RoundedCornerShape(10.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("●", color = if (active) Accent else BorderDim, fontSize = 10.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(summary.title, color = if (active) Accent else TextPrimary, fontSize = 14.sp,
                fontWeight = FontWeight.Medium, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(summary.preview, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
        }
        Text(fmtTime(summary.updatedAt), color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(end = 8.dp))
        Text("✕", color = TextMuted, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onDelete).padding(6.dp))
    }
}

// ─────────────────────────── Session ───────────────────────────

@Composable
private fun SessionView(
    viewModel: AppViewModel,
    current: com.ancode.app.model.Session,
    modelLabel: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.messages.collectAsState()
    val streamingText by viewModel.streamingText.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val statusText by viewModel.statusText.collectAsState()
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }

    LaunchedEffect(messages.size, streamingText.length) {
        if (messages.isNotEmpty()) listState.animateScrollToItem((messages.size - 1).coerceAtLeast(0))
    }

    Column(modifier.fillMaxSize().background(BgDeep).imePadding()) {
        // ── title bar (opencode: session title + token/stats right) ──
        Row(
            Modifier.fillMaxWidth().background(BgElevated).padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.width(36.dp)) {
                Icon(Icons.Filled.ArrowBack, "返回", tint = TextMuted, modifier = Modifier.width(18.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(current.title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Spacer(Modifier.height(1.dp))
                Text("workspace ${current.workspaceId.take(6)}", color = TextMuted,
                    fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
            Box(
                Modifier.width(8.dp).height(8.dp)
                    .background(if (isRunning) Accent else Color(0xFF22C55E), RoundedCornerShape(4.dp))
            )
            Spacer(Modifier.width(6.dp))
            Text(statusText.take(16), color = TextSecondary, fontSize = 11.sp, maxLines = 1,
                modifier = Modifier.padding(end = 10.dp))
        }

        // ── message stream (flat, opencode style) ──
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (messages.isEmpty() && streamingText.isEmpty()) item { EmptyHint() }
            items(messages, key = { "${it.createdAt}-${it.role}-${it.content?.hashCode()}" }) { msg ->
                StreamMessage(msg)
            }
            if (streamingText.isNotBlank()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Ancode", color = TextMuted, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        Text(streamingText, color = TextPrimary, fontSize = 14.sp)
                        Text("▍", color = Accent, fontSize = 14.sp)
                    }
                }
            }
        }

        // ── bottom prompt (opencode: left-bordered input card) ──
        Column(Modifier.fillMaxWidth().background(BgDeep).padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(BgElevated, RoundedCornerShape(12.dp))
                    .border(1.dp, Accent.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text("❯", color = Accent, fontFamily = FontFamily.Monospace, fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 10.dp))
                Spacer(Modifier.width(6.dp))
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入任务… 以 ! 开头执行终端命令", color = TextMuted) },
                    minLines = 1, maxLines = 5,
                    shape = RoundedCornerShape(10.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
                Spacer(Modifier.width(6.dp))
                IconButton(
                    onClick = {
                        if (isRunning) viewModel.stopAgent()
                        else if (input.isNotBlank()) {
                            if (input.startsWith("!")) viewModel.runQuickCommand(input.removePrefix("!").trim())
                            else viewModel.sendUserMessage(input)
                            input = ""
                        }
                    },
                    modifier = Modifier
                        .background(if (isRunning) Color(0xFFEF4444) else Accent, RoundedCornerShape(10.dp))
                        .width(42.dp).height(42.dp)
                ) {
                    Icon(if (isRunning) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send,
                        if (isRunning) "停止" else "发送", tint = Color.White, modifier = Modifier.width(18.dp))
                }
            }
            // footer status row (opencode footer)
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("● $modelLabel", color = if (isRunning) Accent else TextSecondary,
                    fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                Spacer(Modifier.weight(1f))
                Text("⏎ 发送 · ! 命令 · 会话标题栏可切换", color = TextMuted,
                    fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun EmptyHint() {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("❯ ancode", color = Accent, fontFamily = FontFamily.Monospace, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Text("描述一个任务，Agent 将在 Ubuntu 环境中自主完成", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Text("工具调用与 Do List 以卡片形式实时展示", color = TextMuted, fontSize = 12.sp)
    }
}

// ─────────────────── message rendering (opencode style) ───────────────────

@Composable
private fun StreamMessage(msg: ChatMessage) {
    when (msg.role) {
        Role.USER -> UserCard(msg.content ?: "")
        Role.ASSISTANT -> AssistantBlock(msg)
        Role.TOOL -> Unit // tool results shown inside inline tool rows
        Role.SYSTEM -> Unit
    }
}

/** User message: dark card with blue left border (like opencode). */
@Composable
private fun UserCard(text: String) {
    Row(Modifier.fillMaxWidth()) {
        Spacer(Modifier.width(2.dp))
        Box(
            Modifier
                .weight(1f)
                .background(BgElevated, RoundedCornerShape(10.dp))
                .border(1.dp, Accent.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(text, color = TextPrimary, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

/** Assistant message: plain text + markdown + inline tool rows. */
@Composable
private fun AssistantBlock(msg: ChatMessage) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        msg.content?.takeIf { it.isNotBlank() }?.let { content ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Ancode", color = TextMuted, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                MarkdownBody(content)
            }
        }
        msg.toolCalls.forEach { tc -> InlineToolRow(tc) }
    }
}

/** Markdown paragraphs + code blocks. */
@Composable
private fun MarkdownBody(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        com.ancode.app.ui.Markdown.parseBlocks(text).forEach { block ->
            when (block.type) {
                "code" -> CodeBlock(block.text, block.lang)
                else -> Text(
                    com.ancode.app.ui.Markdown.renderBlock(block),
                    color = TextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

/** Code block with language label header (terminal style). */
@Composable
private fun CodeBlock(code: String, lang: String?) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF0D1420), RoundedCornerShape(10.dp))
            .border(1.dp, BorderDim, RoundedCornerShape(10.dp))
    ) {
        if (!lang.isNullOrBlank()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF151E2C))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("$ ", color = Accent, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                Spacer(Modifier.width(4.dp))
                Text(lang, color = TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        }
        Text(
            code,
            color = Color(0xFF9CDCFE),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth()
        )
    }
}

/** OpenCode inline tool row: `✱ Grep "pattern"` — tap to expand result. */
@Composable
private fun InlineToolRow(tc: ToolCall) {
    var expanded by remember { mutableStateOf(false) }
    val statusColor = when (tc.status) {
        "success" -> Success
        "error" -> Error
        "running" -> Accent
        else -> TextMuted
    }
    val icon = when (tc.status) {
        "running" -> "│"
        "success" -> "✓"
        "error" -> "✗"
        else -> "·"
    }
    val label = toolLabel(tc)
    val summary = toolArgsSummary(tc)

    Column(
        Modifier
            .fillMaxWidth()
            .background(BgElevated, RoundedCornerShape(8.dp))
            .border(1.dp, BorderDim, RoundedCornerShape(8.dp))
            .clickable { expanded = !expanded }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, color = statusColor, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            Spacer(Modifier.width(8.dp))
            Text(label, color = statusColor, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(6.dp))
            Text(summary, color = TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                maxLines = 1, modifier = Modifier.weight(1f))
            if (tc.status == "running") {
                Text("…", color = Accent, fontSize = 14.sp)
            } else {
                tc.durationMs?.let { d ->
                    Text("${d}ms", color = TextMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                }
            }
        }
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            val detail = when {
                tc.error != null -> tc.error
                tc.result != null -> tc.result
                else -> tc.arguments
            }
            Text(
                detail.take(1500),
                color = if (tc.error != null) Error else TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun toolLabel(tc: ToolCall): String = when (tc.name) {
    "terminal" -> "bash"
    "todo" -> "Todo"
    else -> tc.name
}

private fun toolArgsSummary(tc: ToolCall): String {
    val raw = tc.arguments
    return runCatching {
        val el = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.parseToJsonElement(raw)
        val o = el as? kotlinx.serialization.json.JsonObject ?: return ""
        when (tc.name) {
            "grep", "glob" -> o["pattern"]?.toString()?.trim('"').orEmpty()
            "read_file", "write_file", "edit_file" -> o["path"]?.toString()?.trim('"').orEmpty()
            "terminal" -> o["command"]?.toString()?.trim('"')?.take(40).orEmpty()
            "todo" -> o["op"]?.toString()?.trim('"').orEmpty()
            else -> raw.take(30)
        }
    }.getOrDefault(raw.take(30))
}

private fun fmtTime(ts: Long): String {
    val fmt = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
    return fmt.format(java.util.Date(ts))
}