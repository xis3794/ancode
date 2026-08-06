package com.ancode.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ancode.app.AppViewModel
import com.ancode.app.model.SessionSummary
import com.ancode.app.ui.theme.Accent
import com.ancode.app.ui.theme.BgDeep
import com.ancode.app.ui.theme.BgElevated
import com.ancode.app.ui.theme.BorderDim
import com.ancode.app.ui.theme.TextMuted
import com.ancode.app.ui.theme.TextPrimary
import com.ancode.app.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SessionScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val sessions by viewModel.sessions.collectAsState()
    val currentId = viewModel.currentSession.collectAsState().value?.id

    Column(
        modifier
            .fillMaxSize()
            .background(BgDeep)
            .padding(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("会话", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { viewModel.createSession() }) {
                Icon(Icons.Filled.Add, "新建会话", tint = Accent)
            }
        }
        Spacer(Modifier.padding(4.dp))
        if (sessions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无会话，点击 + 新建", color = TextMuted)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sessions, key = { it.id }) { s ->
                    SessionCard(
                        summary = s,
                        active = s.id == currentId,
                        onClick = { viewModel.loadSession(s.id) },
                        onDelete = { viewModel.deleteSession(s.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionCard(
    summary: SessionSummary,
    active: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (active) Color(0xFF16233A) else BgElevated, RoundedCornerShape(12.dp))
            .border(1.dp, if (active) Accent.copy(alpha = 0.6f) else BorderDim, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                summary.title,
                color = if (active) Accent else TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1
            )
            Spacer(Modifier.padding(2.dp))
            Text(
                summary.preview,
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
            Spacer(Modifier.padding(2.dp))
            Text(
                "${formatTime(summary.updatedAt)} · ${summary.messageCount} 条消息 · ${summary.todoCount} 待办",
                color = TextMuted,
                style = MaterialTheme.typography.labelSmall
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.DeleteOutline, "删除", tint = TextMuted, modifier = Modifier.size(18.dp))
        }
    }
}

private fun formatTime(ts: Long): String {
    val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return fmt.format(Date(ts))
}