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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UnfoldMore
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
import com.ancode.app.model.Role
import com.ancode.app.ui.components.MessageItem
import com.ancode.app.ui.theme.Accent
import com.ancode.app.ui.theme.BgDeep
import com.ancode.app.ui.theme.BgElevated
import com.ancode.app.ui.theme.BorderDim
import com.ancode.app.ui.theme.Cyan
import com.ancode.app.ui.theme.TextMuted
import com.ancode.app.ui.theme.TextPrimary
import com.ancode.app.ui.theme.TextSecondary

/**
 * Chat tab — an OpenCode-style two-state TUI:
 *  - Home: centered logo + central prompt + session list (destination picker)
 *  - Session: message stream + tool cards + bottom prompt + footer status row
 */
@Composable
fun ChatScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val current = viewModel.currentSession.collectAsState().value
    val sessions by viewModel.sessions.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val streamingText by viewModel.streamingText.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val statusText by viewModel.statusText.collectAsState()
    val settings by viewModel.settingsFlow.collectAsState()
    var showSessions by remember { mutableStateOf(current == null) }

    LaunchedEffect(current?.id) {
        if (current != null) showSessions = false
    }

    if (showSessions || current == null) {
        HomeScreen(
            sessions = sessions,
            currentId = current?.id,
            onOpen = { id ->
                viewModel.loadSession(id)
                showSessions = false
            },
            onNew = {
                viewModel.createSession()
                showSessions = false
            },
            onDelete = { viewModel.deleteSession(it) },
            onPromptSubmit = { text ->
                if (text.isNotBlank()) {
                    viewModel.newSessionAndSend(text)
                    showSessions = false
                }
            },
            modelLabel = settings.providers.firstOrNull { it.id == settings.activeProviderId }?.name ?: "未配置模型",
            modifier = modifier
        )
        return
    }

    SessionScreen(
        viewModel = viewModel,
        current = current,
        messages = messages,
        streamingText = streamingText,
        isRunning = isRunning,
        statusText = statusText,
        modelLabel = settings.providers.firstOrNull { it.id == settings.activeProviderId }?.name ?: "未配置模型",
        onBackToList = { showSessions = true },
        modifier = modifier
    )
}

/** OpenCode-style Home: centered logo, central prompt, session destination list. */
@Composable
private fun HomeScreen(
    sessions: List<com.ancode.app.model.SessionSummary>,
    currentId: String?,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onDelete: (String) -> Unit,
    onPromptSubmit: (String) -> Unit,
    modelLabel: String,
    modifier: Modifier = Modifier
) {
    var input by remember { mutableStateOf("") }

    Column(
        modifier
            .fillMaxSize()
            .background(BgDeep)
            .imePadding()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))

        // ── logo (opencode style: minimal wordmark) ──
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "❯ ancode",
                color = Accent,
                fontFamily = FontFamily.Monospace,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "vibe coding agent on Android",
                color = TextMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "model: $modelLabel",
                color = TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }

        Spacer(Modifier.height(28.dp))

        // ── central prompt (opencode home prompt) ──
        Row(
            Modifier
                .fillMaxWidth(0.92f)
                .background(BgElevated, RoundedCornerShape(12.dp))
                .border(1.dp, Accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("❯", color = Accent, fontFamily = FontFamily.Monospace, fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("描述一个任务…", color = TextMuted, fontSize = 15.sp) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            IconButton(
                onClick = { onPromptSubmit(input.trim()) },
                modifier = Modifier
                    .background(Accent, RoundedCornerShape(10.dp))
                    .width(40.dp)
                    .height(40.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White, modifier = Modifier.width(18.dp))
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── session destination list ──
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("SESSIONS", color = TextMuted, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text("+ 新建", color = Accent, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onNew).padding(4.dp))
        }
        Spacer(Modifier.height(6.dp))

        if (sessions.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无会话 — 在上方输入任务开始", color = TextMuted, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(sessions, key = { it.id }) { s ->
                    HomeSessionRow(s, s.id == currentId, onClick = { onOpen(s.id) }, onDelete = { onDelete(s.id) })
                }
            }
        }
    }
}

@Composable
private fun HomeSessionRow(
    summary: com.ancode.app.model.SessionSummary,
    active: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (active) Color(0xFF16233A) else BgElevated, RoundedCornerShape(10.dp))
            .border(1.dp, if (active) Accent.copy(alpha = 0.6f) else BorderDim, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("❯", color = if (active) Accent else BorderDim, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                summary.title,
                color = if (active) Accent else TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(
                summary.preview,
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1
            )
        }
        Text(
            "${formatTime(summary.updatedAt)}",
            color = TextMuted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            "✕",
            color = TextMuted,
            fontSize = 13.sp,
            modifier = Modifier
                .clickable(onClick = onDelete)
                .padding(6.dp)
        )
    }
}

/** OpenCode-style session: status row + stream + bottom prompt + footer. */
@Composable
private fun SessionScreen(
    viewModel: AppViewModel,
    current: com.ancode.app.model.Session,
    messages: List<com.ancode.app.model.ChatMessage>,
    streamingText: String,
    isRunning: Boolean,
    statusText: String,
    modelLabel: String,
    onBackToList: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }

    LaunchedEffect(messages.size, streamingText.length) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem((messages.size - 1).coerceAtLeast(0))
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(BgDeep)
            .imePadding()
    ) {
        // ── top status row (session title · workspace · status) ──
        Row(
            Modifier
                .fillMaxWidth()
                .background(BgElevated)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .width(8.dp)
                    .height(8.dp)
                    .background(
                        if (isRunning) Accent else Color(0xFF22C55E),
                        RoundedCornerShape(4.dp)
                    )
            )
            Spacer(Modifier.width(8.dp))
            Text(
                current.title.take(16),
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "ws:${current.workspaceId.take(6)}",
                color = Cyan,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
            Spacer(Modifier.weight(1f))
            Text(statusText.take(20), color = TextSecondary, fontSize = 11.sp, maxLines = 1)
            Spacer(Modifier.width(6.dp))
            // session switcher chip
            Row(
                Modifier
                    .background(BgDeep, RoundedCornerShape(8.dp))
                    .border(1.dp, BorderDim, RoundedCornerShape(8.dp))
                    .clickable(onClick = onBackToList)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("会话", color = Accent, fontSize = 11.sp)
                Spacer(Modifier.width(2.dp))
                Icon(Icons.Filled.UnfoldMore, null, tint = TextMuted, modifier = Modifier.width(13.dp))
            }
        }

        // ── message stream ──
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty() && streamingText.isEmpty()) {
                item { EmptySession() }
            }
            items(messages, key = { "${it.createdAt}-${it.role}-${it.content?.hashCode()}" }) { msg ->
                MessageItem(msg)
            }
            if (streamingText.isNotBlank()) {
                item {
                    MessageItem(
                        com.ancode.app.model.ChatMessage(role = Role.ASSISTANT, content = streamingText),
                        streaming = true
                    )
                }
            }
        }

        // ── bottom prompt (opencode style) ──
        Row(
            Modifier
                .fillMaxWidth()
                .background(BgElevated)
                .border(1.dp, BorderDim)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Text("❯", color = Accent, fontFamily = FontFamily.Monospace, fontSize = 17.sp, modifier = Modifier.padding(bottom = 12.dp))
            Spacer(Modifier.width(6.dp))
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入任务… 以 ! 开头执行终端命令", color = TextMuted) },
                minLines = 1,
                maxLines = 5,
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = BgDeep,
                    unfocusedContainerColor = BgDeep,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (isRunning) {
                        viewModel.stopAgent()
                    } else if (input.isNotBlank()) {
                        if (input.startsWith("!")) {
                            viewModel.runQuickCommand(input.removePrefix("!").trim())
                        } else {
                            viewModel.sendUserMessage(input)
                        }
                        input = ""
                    }
                },
                modifier = Modifier
                    .background(if (isRunning) Color(0xFFEF4444) else Accent, RoundedCornerShape(12.dp))
                    .width(46.dp)
                    .height(46.dp)
            ) {
                Icon(
                    if (isRunning) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send,
                    contentDescription = if (isRunning) "停止" else "发送",
                    tint = Color.White
                )
            }
        }

        // ── footer status row (opencode footer) ──
        Row(
            Modifier
                .fillMaxWidth()
                .background(BgElevated)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "~/workspace/${current.workspaceId.take(6)}",
                color = TextMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
            Spacer(Modifier.weight(1f))
            Text(
                "● $modelLabel",
                color = if (isRunning) Accent else TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "⏎ 发送 · ! 命令",
                color = TextMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun EmptySession() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "❯ ancode",
            color = Accent,
            fontFamily = FontFamily.Monospace,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))
        Text("描述任务，Agent 将在 Ubuntu 环境中自主完成", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Text("• 工具调用与 Do List 进度实时展示为卡片", color = TextMuted, fontSize = 12.sp)
        Text("• ! 前缀直接执行终端命令", color = TextMuted, fontSize = 12.sp)
        Text("• 项目文件在应用私有目录，MT 管理器可浏览", color = TextMuted, fontSize = 12.sp)
    }
}

private fun formatTime(ts: Long): String {
    val fmt = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
    return fmt.format(java.util.Date(ts))
}