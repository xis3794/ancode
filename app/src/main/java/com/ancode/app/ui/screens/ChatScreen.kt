package com.ancode.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.ancode.app.AppViewModel
import com.ancode.app.model.Role
import com.ancode.app.ui.components.MessageItem
import com.ancode.app.ui.theme.Accent
import com.ancode.app.ui.theme.BgDeep
import com.ancode.app.ui.theme.BgElevated
import com.ancode.app.ui.theme.BorderDim
import com.ancode.app.ui.theme.TextMuted
import com.ancode.app.ui.theme.TextSecondary

@Composable
fun ChatScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.messages.collectAsState()
    val streamingText by viewModel.streamingText.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val statusText by viewModel.statusText.collectAsState()
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }

    // auto-scroll to bottom on new content
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
        // status bar
        Row(
            Modifier
                .fillMaxWidth()
                .background(BgElevated)
                .padding(horizontal = 12.dp, vertical = 6.dp),
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
            Text(statusText, color = TextSecondary, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            Text(
                "guest:/root/projects",
                color = TextMuted,
                style = MaterialTheme.typography.labelSmall
            )
        }

        // messages
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty() && streamingText.isEmpty()) {
                item { EmptyState() }
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

        // input bar
        Row(
            Modifier
                .fillMaxWidth()
                .background(BgElevated)
                .border(1.dp, BorderDim)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入任务… 以 ! 开头可直接执行终端命令", color = TextMuted) },
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
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "ANCODE",
            color = Accent,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text("移动端 vibe coding Agent", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        Text("• 直接描述任务，Agent 会在 Ubuntu 环境里干活", color = TextMuted, style = MaterialTheme.typography.bodySmall)
        Text("• 命令前加 ! 可快速执行终端命令", color = TextMuted, style = MaterialTheme.typography.bodySmall)
        Text("• 工具调用、Do List 进度实时展示", color = TextMuted, style = MaterialTheme.typography.bodySmall)
    }
}