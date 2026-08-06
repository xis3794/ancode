package com.ancode.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ancode.app.model.ChatMessage
import com.ancode.app.model.Role
import com.ancode.app.model.ToolCall
import com.ancode.app.ui.Markdown
import com.ancode.app.ui.theme.Accent
import com.ancode.app.ui.theme.AccentSoft
import com.ancode.app.ui.theme.BgDeep
import com.ancode.app.ui.theme.BgElevated
import com.ancode.app.ui.theme.BorderDim
import com.ancode.app.ui.theme.Cyan
import com.ancode.app.ui.theme.Error
import com.ancode.app.ui.theme.Success
import com.ancode.app.ui.theme.TextMuted
import com.ancode.app.ui.theme.TextPrimary
import com.ancode.app.ui.theme.TextSecondary
import com.ancode.app.ui.theme.Warning

@Composable
fun MessageItem(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    streaming: Boolean = false
) {
    when (message.role) {
        Role.USER -> UserBubble(message.content ?: "", modifier)
        Role.ASSISTANT -> AssistantMessage(message, modifier, streaming)
        Role.TOOL -> ToolResultRow(message, modifier)
        Role.SYSTEM -> Unit
    }
}

@Composable
private fun UserBubble(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            Modifier
                .widthIn(max = 320.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(text, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun AssistantMessage(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    streaming: Boolean
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .background(Accent.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("ANCODE", color = AccentSoft, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.width(8.dp))
            if (streaming) {
                val transition = rememberInfiniteTransition(label = "cursor")
                val alpha by transition.animateFloat(
                    initialValue = 1f, targetValue = 0.2f,
                    animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
                    label = "cursorAlpha"
                )
                Text("▍", color = Accent, modifier = Modifier.alpha(alpha))
            }
        }
        message.content?.let { content ->
            if (content.isNotBlank()) {
                MarkdownBlock(content)
            }
        }
        message.toolCalls.forEach { tc ->
            ToolCallCard(tc)
        }
    }
}

@Composable
fun MarkdownBlock(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Markdown.parseBlocks(text).forEach { block ->
            when (block.type) {
                "code" -> CodeBlock(block.text, block.lang)
                "hr" -> Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(BorderDim, RoundedCornerShape(1.dp))
                        .height(1.dp)
                )
                else -> Text(
                    Markdown.renderBlock(block),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )
            }
        }
    }
}

@Composable
fun CodeBlock(code: String, lang: String?) {
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
                Icon(Icons.Filled.Code, null, tint = AccentSoft, modifier = Modifier.width(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(lang, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
        }
        Text(
            code,
            fontFamily = FontFamily.Monospace,
            fontSize = MaterialTheme.typography.bodySmall.fontSize,
            lineHeight = 18.sp,
            color = Cyan,
            modifier = Modifier
                .padding(12.dp)
                .verticalScroll(rememberScrollState())
        )
    }
}

@Composable
private fun ToolResultRow(message: ChatMessage, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(
            Modifier
                .widthIn(max = 320.dp)
                .background(Color(0xFF10161F), RoundedCornerShape(8.dp))
                .border(1.dp, BorderDim, RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            Column {
                Text(
                    "↳ ${message.toolName ?: "tool"} 结果",
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(Modifier.width(0.dp))
                Text(
                    (message.content ?: "").take(400),
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 6
                )
            }
        }
    }
}

private fun toolIcon(name: String): ImageVector = when (name) {
    "terminal" -> Icons.Filled.Terminal
    "read_file" -> Icons.Outlined.Description
    "write_file", "edit_file" -> Icons.Outlined.EditNote
    "glob", "grep" -> Icons.Outlined.Search
    "todo" -> Icons.Outlined.ListAlt
    else -> Icons.Outlined.FolderOpen
}

@Composable
fun ToolCallCard(tc: ToolCall, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val statusColor = when (tc.status) {
        "success" -> Success
        "error" -> Error
        "running" -> Accent
        else -> TextMuted
    }
    val statusText = when (tc.status) {
        "success" -> "完成 ${tc.durationMs?.let { "${it}ms" } ?: ""}"
        "error" -> "失败"
        "running" -> "运行中..."
        else -> "待执行"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = BgElevated),
        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
    ) {
        Column(Modifier.clickable { expanded = !expanded }) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(toolIcon(tc.name), null, tint = statusColor, modifier = Modifier.width(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tc.name, color = TextPrimary, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                Text(statusText, color = statusColor, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.width(4.dp))
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    null,
                    tint = TextMuted,
                    modifier = Modifier.width(18.dp)
                )
            }
            if (expanded) {
                Column(
                    Modifier
                        .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        "参数",
                        color = TextMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        prettyJson(tc.arguments),
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (tc.error != null) {
                        Spacer(Modifier.width(4.dp))
                        Text("错误", color = Error, style = MaterialTheme.typography.labelSmall)
                        Text(
                            tc.error,
                            color = Error,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else if (tc.result != null) {
                        Spacer(Modifier.width(4.dp))
                        Text("结果", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                        Text(
                            tc.result.take(800),
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

private fun prettyJson(raw: String): String {
    return runCatching {
        val parsed = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .parseToJsonElement(raw)
        kotlinx.serialization.json.Json { prettyPrint = true }
            .encodeToString(kotlinx.serialization.json.JsonElement.serializer(), parsed)
    }.getOrDefault(raw.take(300))
}