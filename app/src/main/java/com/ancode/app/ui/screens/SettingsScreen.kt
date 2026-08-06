package com.ancode.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ancode.app.AppViewModel
import com.ancode.app.linux.RootfsManager
import com.ancode.app.ui.theme.Accent
import com.ancode.app.ui.theme.BgDeep
import com.ancode.app.ui.theme.BgElevated
import com.ancode.app.ui.theme.BorderDim
import com.ancode.app.ui.theme.Error
import com.ancode.app.ui.theme.Success
import com.ancode.app.ui.theme.TextMuted
import com.ancode.app.ui.theme.TextPrimary
import com.ancode.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settingsFlow.collectAsState()
    var baseUrl by remember { mutableStateOf(settings.baseUrl) }
    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var model by remember { mutableStateOf(settings.model) }
    var workingDir by remember { mutableStateOf(settings.workingDir) }
    val rootfsState by viewModel.rootfs.state.collectAsState()
    val scope = rememberCoroutineScope()
    var probeResult by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(settings) {
        baseUrl = settings.baseUrl
        apiKey = settings.apiKey
        model = settings.model
        workingDir = settings.workingDir
    }

    Column(
        modifier
            .fillMaxSize()
            .background(BgDeep)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("设置", color = TextPrimary, style = MaterialTheme.typography.titleMedium)

        // ---- LLM ----
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = BgElevated)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("LLM（OpenAI 兼容）", color = Accent, style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("API Base URL", color = TextMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key", color = TextMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("模型名", color = TextMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = workingDir,
                    onValueChange = { workingDir = it },
                    label = { Text("工作目录（guest 内）", color = TextMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "预设：DeepSeek https://api.deepseek.com · OpenAI https://api.openai.com/v1 · 通义 compatible-mode/v1 · 智谱 api/paas/v4 · Ollama http://<ip>:11434/v1",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                Button(
                    onClick = {
                        viewModel.saveSettings(baseUrl, apiKey, model, workingDir)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) {
                    Text("保存", color = Color.White)
                }
            }
        }

        // ---- Linux env ----
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = BgElevated)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Memory, null, tint = Accent, modifier = Modifier.width(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Linux 环境（proot Ubuntu 24.04 arm64）", color = Accent, style = MaterialTheme.typography.titleSmall)
                }
                val status = when (rootfsState.status) {
                    RootfsManager.Status.READY -> "✅ 已就绪"
                    RootfsManager.Status.DOWNLOADING -> "⏳ 下载中 ${(rootfsState.progress * 100).toInt()}%"
                    RootfsManager.Status.EXTRACTING -> "⏳ 解压中..."
                    RootfsManager.Status.ERROR -> "❌ ${rootfsState.error ?: "错误"}"
                    else -> "○ 未安装"
                }
                Text(status, color = when (rootfsState.status) {
                    RootfsManager.Status.READY -> Success
                    RootfsManager.Status.ERROR -> Error
                    else -> TextSecondary
                }, style = MaterialTheme.typography.bodyMedium)

                if (rootfsState.status == RootfsManager.Status.DOWNLOADING) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(BorderDim, RoundedCornerShape(6.dp))
                            .padding(vertical = 4.dp)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(rootfsState.progress.coerceIn(0f, 1f))
                                .background(Accent, RoundedCornerShape(6.dp))
                                .padding(vertical = 4.dp)
                        ) {}
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { viewModel.installRootfs() },
                        enabled = rootfsState.status != RootfsManager.Status.DOWNLOADING &&
                            rootfsState.status != RootfsManager.Status.EXTRACTING
                    ) {
                        Icon(Icons.Filled.Download, null, modifier = Modifier.width(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (rootfsState.status == RootfsManager.Status.READY) "重新安装" else "安装环境")
                    }
                    TextButton(onClick = {
                        scope.launch {
                            probeResult = viewModel.probeLinux()
                        }
                    }) {
                        Text("环境自检")
                    }
                }
                probeResult?.let {
                    Text(
                        it,
                        color = TextSecondary,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // ---- About ----
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = BgElevated)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Ancode v0.1.0", color = TextPrimary, style = MaterialTheme.typography.titleSmall)
                Text(
                    "Android 原生 vibe coding Agent · Kotlin/Compose · proot Ubuntu · OpenAI 兼容\n" +
                        "工具：terminal / read_file / write_file / edit_file / glob / grep / todo\n" +
                        "Roadmap：MCP 支持 · Skills · Anthropic/Gemini 适配器",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}