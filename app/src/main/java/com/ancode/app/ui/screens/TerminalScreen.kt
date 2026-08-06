package com.ancode.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ancode.app.AppViewModel
import com.ancode.app.linux.Pty
import com.ancode.app.ui.AnsiTerminal
import com.ancode.app.ui.theme.BgDeep
import com.ancode.app.ui.theme.BgElevated
import com.ancode.app.ui.theme.TextMuted
import com.ancode.app.ui.theme.TextPrimary
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Interactive terminal backed by a real PTY running proot + bash inside
 * the Ubuntu rootfs. Renders through the built-in ANSI emulator.
 */
@Composable
fun TerminalScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var session by remember { mutableStateOf<Pty.Session?>(null) }
    var term by remember { mutableStateOf(AnsiTerminal(80, 24)) }
    var input by remember { mutableStateOf("") }
    var output by remember { mutableIntStateOf(0) }  // bump to re-render
    var collectJob by remember { mutableStateOf<Job?>(null) }

    fun startShell() {
        if (!viewModel.rootfs.isReady()) return
        collectJob?.cancel()
        session?.close()
        val t = AnsiTerminal(80, 24)
        term = t
        scope.launch {
            val cmd = viewModel.rootfs.let { r ->
                com.ancode.app.linux.ProotRunner(r).buildCommand(
                    workDir = "/root",
                    extraArgs = listOf("/bin/bash", "-l")
                )
            }
            runCatching {
                val s = Pty.spawn(cmd)
                session = s
                s.output.collect { bytes ->
                    t.feed(String(bytes, Charsets.UTF_8))
                    output++
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (viewModel.rootfs.isReady() && session == null) startShell()
    }

    Column(
        modifier
            .fillMaxSize()
            .background(BgDeep)
    ) {
        // header
        Row(
            Modifier
                .fillMaxWidth()
                .background(BgElevated)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Terminal, null, tint = TextMuted, modifier = Modifier.width(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("ubuntu@ancode:~", color = TextPrimary, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { startShell() }) {
                Icon(Icons.Filled.Refresh, "重启终端", tint = TextMuted, modifier = Modifier.width(18.dp))
            }
        }

        // terminal view
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } }
        ) {
            val rendered = remember(term, output) { term.render() }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp)
            ) {
                rendered.forEach { line ->
                    Text(
                        line,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        color = TextPrimary
                    )
                }
                // input echo line
                Text(
                    "$ ${input}▍",
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    color = TextPrimary
                )
            }
        }

        // input row
        androidx.compose.material3.TextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            placeholder = { Text("输入命令，回车执行", color = TextMuted) },
            singleLine = true,
            colors = androidx.compose.material3.TextFieldDefaults.colors(
                focusedContainerColor = BgElevated,
                unfocusedContainerColor = BgElevated,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSend = {
                    session?.write(input + "\r")
                    input = ""
                }
            )
        )
    }
}