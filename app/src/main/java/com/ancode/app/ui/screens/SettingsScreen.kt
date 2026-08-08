package com.ancode.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.ancode.app.settings.ProviderProfile
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
    val rootfsState by viewModel.rootfs.state.collectAsState()
    val scope = rememberCoroutineScope()
    var probeResult by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<ProviderProfile?>(null) }
    var showNew by remember { mutableStateOf(false) }
    var workingDir by remember { mutableStateOf(settings.workingDir) }

    // SAF launcher: user picks a rootfs .tar.gz from their own file manager
    val pickRootfs = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importRootfsFromUri(uri)
    }

    LaunchedEffect(settings) {
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

        // ---- LLM providers ----
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = BgElevated)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("模型供应商（可多个，切换使用）", color = Accent, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { showNew = true }) {
                        Icon(Icons.Filled.Add, "添加供应商", tint = Accent)
                    }
                }

                if (settings.providers.isEmpty()) {
                    Text(
                        "尚未添加供应商 — 点击右上 + 添加（支持 DeepSeek / 通义 / 智谱 / OpenAI / Ollama / Moonshot / SiliconFlow / 自定义）",
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                settings.providers.forEach { p ->
                    ProviderRow(
                        profile = p,
                        active = p.id == settings.activeProviderId,
                        onSelect = { viewModel.setActiveProvider(p.id) },
                        onEdit = { editing = p },
                        onDelete = { viewModel.deleteProvider(p.id) }
                    )
                }

                Text(
                    "当前使用：${settings.providers.firstOrNull { it.id == settings.activeProviderId }?.name ?: "未选择"} · " +
                        "${settings.providers.firstOrNull { it.id == settings.activeProviderId }?.model ?: ""}",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )

                OutlinedTextField(
                    value = workingDir,
                    onValueChange = { workingDir = it },
                    label = { Text("工作目录（guest 内）", color = TextMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "提示：工作目录映射到应用私有目录 files/projects，可用 MT 管理器免 ROOT 浏览。",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                Button(
                    onClick = { viewModel.saveWorkingDir(workingDir) },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) {
                    Text("保存工作目录", color = Color.White)
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

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.installRootfs() },
                        enabled = rootfsState.status != RootfsManager.Status.DOWNLOADING &&
                            rootfsState.status != RootfsManager.Status.EXTRACTING
                    ) {
                        Icon(Icons.Filled.Download, null, modifier = Modifier.width(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (rootfsState.status == RootfsManager.Status.READY) "重新下载安装" else "下载安装")
                    }
                    Button(
                        onClick = { pickRootfs.launch(arrayOf("*/*")) },
                        enabled = rootfsState.status != RootfsManager.Status.DOWNLOADING &&
                            rootfsState.status != RootfsManager.Status.EXTRACTING,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E40AF))
                    ) {
                        Icon(Icons.Filled.Memory, null, modifier = Modifier.width(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("从本地选择")
                    }
                    TextButton(onClick = {
                        scope.launch { probeResult = viewModel.probeLinux() }
                    }) {
                        Text("自检")
                    }
                }
                Text(
                    "若下载失败：下载 ubuntu-base-24.04.3-base-arm64.tar.gz（约28MB），\n" +
                        "点击「从本地选择」从文件管理器选取导入（无需存储权限）。\n" +
                        "镜像：https://mirrors.aliyun.com/ubuntu-cdimage/ubuntu-base/releases/24.04.3/release/",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
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
                Text("Ancode v0.2.0", color = TextPrimary, style = MaterialTheme.typography.titleSmall)
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

    // ---- provider editor dialog ----
    if (editing != null || showNew) {
        ProviderEditorDialog(
            initial = editing,
            onDismiss = { editing = null; showNew = false },
            onSave = { profile ->
                viewModel.saveProvider(profile, makeActive = editing == null)
                editing = null
                showNew = false
            }
        )
    }
}

@Composable
private fun ProviderRow(
    profile: ProviderProfile,
    active: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (active) Color(0xFF16233A) else BgDeep, RoundedCornerShape(10.dp))
            .clickable(onClick = onSelect)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(10.dp)
                .height(10.dp)
                .background(if (active) Accent else BorderDim, RoundedCornerShape(5.dp))
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                profile.name + (if (active) " ✓" else ""),
                color = if (active) Accent else TextPrimary,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                "${profile.model} · ${profile.baseUrl}",
                color = TextMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
        IconButton(onClick = onEdit, modifier = Modifier.width(32.dp)) {
            Icon(Icons.Filled.Edit, "编辑", tint = TextMuted, modifier = Modifier.width(16.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.width(32.dp)) {
            Icon(Icons.Filled.DeleteOutline, "删除", tint = TextMuted, modifier = Modifier.width(16.dp))
        }
    }
}

@Composable
private fun ProviderEditorDialog(
    initial: ProviderProfile?,
    onDismiss: () -> Unit,
    onSave: (ProviderProfile) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "自定义") }
    var baseUrl by remember { mutableStateOf(initial?.baseUrl ?: "https://api.deepseek.com") }
    var apiKey by remember { mutableStateOf(initial?.apiKey ?: "") }
    var model by remember { mutableStateOf(initial?.model ?: "deepseek-chat") }

    val presets = mapOf(
        "DeepSeek" to ("https://api.deepseek.com" to "deepseek-chat"),
        "通义千问" to ("https://dashscope.aliyuncs.com/compatible-mode/v1" to "qwen-plus"),
        "智谱GLM" to ("https://open.bigmodel.cn/api/paas/v4" to "glm-4-flash"),
        "OpenAI" to ("https://api.openai.com/v1" to "gpt-4o-mini"),
        "Ollama(本地)" to ("http://127.0.0.1:11434/v1" to "qwen2.5-coder:7b"),
        "Moonshot" to ("https://api.moonshot.cn/v1" to "moonshot-v1-8k"),
        "SiliconFlow" to ("https://api.siliconflow.cn/v1" to "deepseek-ai/DeepSeek-V3")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        title = { Text(if (initial == null) "添加供应商" else "编辑供应商", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initial == null) {
                    Text("快捷填充预设：", color = TextMuted, style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        presets.keys.take(4).forEach { k ->
                            TextButton(onClick = {
                                val (u, m) = presets[k]!!
                                name = k
                                baseUrl = u
                                model = m
                            }) { Text(k, style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        presets.keys.drop(4).forEach { k ->
                            TextButton(onClick = {
                                val (u, m) = presets[k]!!
                                name = k
                                baseUrl = u
                                model = m
                            }) { Text(k, style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称", color = TextMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        ProviderProfile(
                            id = initial?.id ?: com.ancode.app.settings.AppSettings.newId(),
                            name = name.ifBlank { "自定义" },
                            baseUrl = baseUrl.ifBlank { "https://api.deepseek.com" },
                            apiKey = apiKey.trim(),
                            model = model.ifBlank { "deepseek-chat" }
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) { Text("保存", color = Color.White) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextMuted) }
        }
    )
}