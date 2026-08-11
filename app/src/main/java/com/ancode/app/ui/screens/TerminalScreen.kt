package com.ancode.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ancode.app.AppViewModel
import com.ancode.app.linux.Pty
import com.ancode.app.ui.AnsiTerminal
import com.ancode.app.ui.theme.Accent
import com.ancode.app.ui.theme.AnsiGreen
import com.ancode.app.ui.theme.BgDeep
import com.ancode.app.ui.theme.BgElevated
import com.ancode.app.ui.theme.BorderDim
import com.ancode.app.ui.theme.TextMuted
import com.ancode.app.ui.theme.TextPrimary
import com.ancode.app.ui.theme.TextSecondary
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Interactive terminal backed by a real PTY running proot + bash inside the
 * Ubuntu rootfs. Output is rendered through the built-in ANSI emulator; the
 * PTY itself echoes typed characters, so the input field is cleared only after
 * the bytes are actually written.
 */
@Composable
fun TerminalScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    // holder survives recomposition, so callbacks always see the live session
    val holder = remember { TerminalHolder() }
    var input by remember { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }
    var ready by remember { mutableStateOf(viewModel.rootfs.isReady()) }
    val listState = rememberLazyListState()

    fun startShell() {
        if (!viewModel.rootfs.isReady()) return
        holder.job?.cancel()
        holder.session?.close()
        val t = AnsiTerminal(80, 24)
        holder.term = t
        revision++
        holder.job = scope.launch {
            val cmd = com.ancode.app.linux.ProotRunner(viewModel.rootfs).buildCommand(
                workDir = "/root/projects",
                extraArgs = listOf("/bin/bash", "-l")
            )
            runCatching {
                val s = Pty.spawn(cmd)
                holder.session = s
                s.output.collect { bytes ->
                    t.feed(String(bytes, Charsets.UTF_8))
                    revision++
                }
            }.onFailure { e ->
                t.feed("\r\n[pty error] ${e.message}\r\n")
                revision++
            }
        }
    }

    LaunchedEffect(Unit) {
        ready = viewModel.rootfs.isReady()
        if (ready && holder.session == null) startShell()
    }

    DisposableEffect(Unit) {
        onDispose {
            holder.job?.cancel()
            holder.session?.close()
        }
    }

    val lines = remember(revision) { holder.term.render() }

    LaunchedEffect(revision) {
        if (lines.isNotEmpty()) listState.scrollToItem((lines.size - 1).coerceAtLeast(0))
    }

    Column(
        modifier
            .fillMaxSize()
            .background(BgDeep)
            .imePadding()
    ) {
        // ── header ──
        Row(
            Modifier
                .fillMaxWidth()
                .background(BgDeep)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "TERMINAL",
                color = TextMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(10.dp))
            Text(
                if (holder.session?.isClosed() == false) "ubuntu@ancode" else "未连接",
                color = if (holder.session?.isClosed() == false) AnsiGreen else TextMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { startShell() }, modifier = Modifier.width(32.dp).height(32.dp)) {
                Icon(Icons.Filled.Refresh, "重启终端", tint = TextMuted, modifier = Modifier.width(16.dp))
            }
        }

        // ── output ──
        if (!ready) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "Linux 环境未安装，请先到设置页安装",
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
            ) {
                itemsIndexed(lines) { _, line ->
                    Text(
                        line,
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // ── input ──
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .background(BgElevated, RoundedCornerShape(10.dp))
                .padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$", color = Accent, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            Spacer(Modifier.width(6.dp))
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text("输入命令，回车执行", color = TextMuted, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                ),
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
                        val s = holder.session
                        if (s != null && !s.isClosed()) {
                            s.write(input + "\n")
                            input = ""
                        }
                    }
                )
            )
        }

        // ── hint row ──
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "proot ubuntu 24.04 · workspace ${viewModel.rootfs.currentWorkspace.take(8)}",
                color = TextMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
            Spacer(Modifier.weight(1f))
            Text(
                "回车发送",
                color = TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(BorderDim))
    }
}

/** Mutable holder so PTY state is never captured stale by callbacks. */
private class TerminalHolder {
    var session: Pty.Session? = null
    var job: Job? = null
    var term: AnsiTerminal = AnsiTerminal(80, 24)
}