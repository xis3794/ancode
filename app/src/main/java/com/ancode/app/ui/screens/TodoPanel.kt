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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.ancode.app.AppViewModel
import com.ancode.app.model.TodoItem
import com.ancode.app.ui.theme.Accent
import com.ancode.app.ui.theme.BgDeep
import com.ancode.app.ui.theme.BgElevated
import com.ancode.app.ui.theme.BorderDim
import com.ancode.app.ui.theme.Success
import com.ancode.app.ui.theme.TextMuted
import com.ancode.app.ui.theme.TextPrimary
import com.ancode.app.ui.theme.TextSecondary

/** Do List side panel — live progress of the agent's plan. */
@Composable
fun TodoPanel(
    todos: List<TodoItem>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxSize()
            .background(BgDeep)
            .border(1.dp, BorderDim)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(BgElevated)
                .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("DO LIST", color = Accent, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            val done = todos.count { it.done }
            Text(
                "$done/${todos.size}",
                color = TextSecondary,
                style = MaterialTheme.typography.labelMedium
            )
            IconButton(onClick = onClear, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Filled.Clear, "清空", tint = TextMuted, modifier = Modifier.size(16.dp))
            }
        }
        if (todos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无任务\nAgent 会用 todo 工具规划步骤", color = TextMuted, style = MaterialTheme.typography.bodySmall)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(todos, key = { it.id }) { item ->
                    TodoRow(item, onToggle)
                }
            }
        }
    }
}

@Composable
private fun TodoRow(item: TodoItem, onToggle: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(BgElevated, RoundedCornerShape(10.dp))
            .clickable { onToggle(item.id) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // checkbox
        Box(
            Modifier
                .size(20.dp)
                .background(
                    if (item.done) Success else Color.Transparent,
                    RoundedCornerShape(5.dp)
                )
                .border(1.dp, if (item.done) Success else BorderDim, RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (item.done) {
                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            item.content,
            color = if (item.done) TextMuted else TextPrimary,
            textDecoration = if (item.done) TextDecoration.LineThrough else null,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.alpha(if (item.done) 0.6f else 1f)
        )
    }
}