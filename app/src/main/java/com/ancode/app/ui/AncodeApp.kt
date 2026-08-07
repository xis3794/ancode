package com.ancode.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ancode.app.AppViewModel
import com.ancode.app.ui.screens.ChatScreen
import com.ancode.app.ui.screens.SettingsScreen
import com.ancode.app.ui.screens.TerminalScreen
import com.ancode.app.ui.theme.BgDeep
import com.ancode.app.ui.theme.BgElevated
import com.ancode.app.ui.theme.TextMuted
import com.ancode.app.ui.theme.TextPrimary

private enum class Tab(val label: String, val icon: ImageVector) {
    CHAT("聊天", Icons.Filled.Chat),
    TERMINAL("终端", Icons.Filled.Terminal),
    SETTINGS("设置", Icons.Filled.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AncodeApp(viewModel: AppViewModel) {
    var tab by remember { mutableStateOf(Tab.CHAT) }
    val current = viewModel.currentSession.collectAsState().value

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (tab) {
                            Tab.CHAT -> if (current != null) "ANCODE · ${current.title.take(18)}" else "ANCODE"
                            Tab.TERMINAL -> "终端 — ubuntu@ancode"
                            Tab.SETTINGS -> "设置"
                        },
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleSmall
                    )
                },
                actions = {
                    when (tab) {
                        Tab.CHAT -> Text(
                            if (current != null) "workspace: /root/projects" else "",
                            color = TextMuted,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        else -> {}
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgElevated)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = BgElevated) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, t.label) },
                        label = { Text(t.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (tab) {
                Tab.CHAT -> ChatScreen(viewModel)
                Tab.TERMINAL -> TerminalScreen(viewModel)
                Tab.SETTINGS -> SettingsScreen(viewModel)
            }
        }
    }
}