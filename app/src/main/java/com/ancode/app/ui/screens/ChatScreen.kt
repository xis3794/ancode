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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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

/** TUI chat tab: Home (session list) <-> Session (message stream + prompt). */
@Composable
fun ChatScreen(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val current = viewModel.currentSession.collectAsState().value
    val sessions by viewModel.sessions.collectAsState()
    val settings by viewModel.settingsFlow.collectAsState()
    var showSessions by remember { mutableStateOf(current == null) }

    LaunchedEffect(current?.id) { if (current != null) showSessions = false }

    val modelLabel = settings.providers.firstOrNull { it.id == settings.activeProviderId }?.model
        ?: settings.providers.firstOrNull()?.model ?: "未配置"

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
    Column(modifier.fillMaxSize().background(BgDeep).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(40.dp))
        // brand block (opencode-style ASCII logo, renamed to Ancode)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                ">",
                color = Accent,
                fontFamily = FontFamily.Monospace,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Ancode",
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "model  $modelLabel",
            color = TextMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        )

        Spacer(Modifier.height(26.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .border(1.dp, BorderDim, RoundedCornerShape(4.dp))
                .clickable(onClick = onNew)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("+", color = Accent, fontFamily = FontFamily.Monospace, fontSize = 18.sp)
            Spacer(Modifier.width(12.dp))
            Text(
                "new session",
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            )
        }

        Spacer(Modifier.height(22.dp))
        Text(
            "SESSIONS",
            color = TextMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))

        if (sessions.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
                Text(
                    "暂无会话",
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
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
            .clickable(onClick = onOpen)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (active) ">" else " ",
            color = Accent,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                summary.title,
                color = if (active) Accent else TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                maxLines = 1
            )
            if (summary.preview.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    summary.preview,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }
        Text(
            fmtTime(summary.updatedAt),
            color = TextMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp
        )
        Text(
            "x",
            color = TextMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.clickable(onClick = onDelete).padding(horizontal = 8.dp, vertical = 4.dp)
        )
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

    val itemCount = messages.size + if (streamingText.isNotBlank()) 1 else 0
    LaunchedEffect(itemCount, streamingText.length) {
        if (itemCount > 0) listState.animateScrollToItem((itemCount - 1).coerceAtLeast(0))
    }

    Column(modifier.fillMaxSize().background(BgDeep).imePadding()) {
        // ── header: back + session title ──
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "<",
                color = TextMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
                modifier = Modifier.clickable(onClick = onBack).padding(end = 10.dp)
            )
            Text(
                current.title,
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Text(
                statusText.take(18),
                color = if (isRunning) Accent else TextMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(BorderDim))

        // ── message stream ──
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (messages.isEmpty() && streamingText.isEmpty()) item { EmptyHint() }
            itemsIndexed(messages) { idx, msg -> StreamMessage(msg, idx) }
            if (streamingText.isNotBlank()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "ancode",
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                        Text(streamingText, color = TextPrimary, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                }
            }
        }

        // ── prompt ──
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (isRunning) Accent else BorderDim, RoundedCornerShape(4.dp))
                    .padding(start = 10.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    ">",
                    color = Accent,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                )
                Spacer(Modifier.width(6.dp))
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            "描述任务，或 ! 执行命令",
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    },
                    maxLines = 5,
                    textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Accent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (!isRunning && input.isNotBlank()) {
                                submit(viewModel, input)
                                input = ""
                            }
                        }
                    )
                )
                IconButton(
                    onClick = {
                        if (isRunning) viewModel.stopAgent()
                        else if (input.isNotBlank()) {
                            submit(viewModel, input)
                            input = ""
                        }
                    },
                    modifier = Modifier.width(40.dp).height(40.dp)
                ) {
                    Icon(
                        if (isRunning) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send,
                        if (isRunning) "停止" else "发送",
                        tint = if (isRunning) Error else Accent,
                        modifier = Modifier.width(17.dp)
                    )
                }
            }
            // footer status line
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    modelLabel,
                    color = if (isRunning) Accent else TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "ws ${current.workspaceId.take(8)}",
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
            }
        }
    }
}

private fun submit(viewModel: AppViewModel, text: String) {
    if (text.startsWith("!")) viewModel.runQuickCommand(text.removePrefix("!").trim())
    else viewModel.sendUserMessage(text)
}

@Composable
private fun EmptyHint() {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "> Ancode",
            color = Accent,
            fontFamily = FontFamily.Monospace,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "描述一个任务，Agent 会在 Ubuntu 环境中自主完成",
            color = TextSecondary,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
    }
}

// ─────────────────── message rendering ───────────────────

@Composable
private fun StreamMessage(msg: ChatMessage, index: Int = 0) {
    when (msg.role) {
        Role.USER -> UserCard(msg.content ?: "")
        Role.ASSISTANT -> AssistantBlock(msg, index)
        Role.TOOL -> Unit
        Role.SYSTEM -> Unit
    }
}

/** User message: bordered block with a left accent bar. */
@Composable
private fun UserCard(text: String) {
    Row(Modifier.fillMaxWidth()) {
        Box(Modifier.width(2.dp).height(20.dp).background(Accent))
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            color = TextPrimary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Assistant message: label + markdown body + inline tool rows. */
@Composable
private fun AssistantBlock(msg: ChatMessage, index: Int = 0) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        msg.content?.takeIf { it.isNotBlank() }?.let { content ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("ancode", color = TextMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                MarkdownBody(content)
            }
        }
        msg.toolCalls.forEach { tc -> InlineToolRow(tc, "$index-${tc.id}") }
    }
}

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

@Composable
private fun CodeBlock(code: String, lang: String?) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(BgElevated, RoundedCornerShape(4.dp))
            .border(1.dp, BorderDim, RoundedCornerShape(4.dp))
    ) {
        if (!lang.isNullOrBlank()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(lang, color = TextMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(BorderDim))
        }
        Text(
            code,
            color = TextSecondary,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(10.dp).fillMaxWidth()
        )
    }
}

/** Inline tool row, e.g. `* Grep "pattern"` — tap to expand output. */
@Composable
private fun InlineToolRow(tc: ToolCall, stateKey: String) {
    var expanded by remember(stateKey) { mutableStateOf(false) }
    val statusColor = when (tc.status) {
        "success" -> Success
        "error" -> Error
        "running" -> Accent
        else -> TextMuted
    }
    val marker = when (tc.status) {
        "running" -> "*"
        "success" -> "+"
        "error" -> "!"
        else -> "-"
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(marker, color = statusColor, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                toolLabel(tc),
                color = statusColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(8.dp))
            Text(
                toolArgsSummary(tc),
                color = TextMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            tc.durationMs?.let { d ->
                Text("${d}ms", color = TextMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
        }
        if (expanded) {
            Spacer(Modifier.height(4.dp))
            val detail = tc.error ?: tc.result ?: tc.arguments
            Text(
                detail.take(2000),
                color = if (tc.error != null) Error else TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgElevated, RoundedCornerShape(4.dp))
                    .padding(8.dp)
            )
        }
    }
}

private fun toolLabel(tc: ToolCall): String = when (tc.name) {
    "terminal" -> "Bash"
    "read_file" -> "Read"
    "write_file" -> "Write"
    "edit_file" -> "Edit"
    "glob" -> "Glob"
    "grep" -> "Grep"
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
            "terminal" -> o["command"]?.toString()?.trim('"')?.take(48).orEmpty()
            "todo" -> o["op"]?.toString()?.trim('"').orEmpty()
            else -> raw.take(36)
        }
    }.getOrDefault(raw.take(36))
}

private fun fmtTime(ts: Long): String {
    val fmt = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
    return fmt.format(java.util.Date(ts))
}