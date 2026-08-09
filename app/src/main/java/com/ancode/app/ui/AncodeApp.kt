package com.ancode.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

private enum class Tab(val label: String, val icon: ImageVector) {
    CHAT("聊天", Icons.Filled.Chat),
    TERMINAL("终端", Icons.Filled.Terminal),
    SETTINGS("设置", Icons.Filled.Settings)
}

/** App shell: bottom nav only — screens render their own title bars (TUI style). */
@Composable
fun AncodeApp(viewModel: AppViewModel) {
    var tab by remember { mutableStateOf(Tab.CHAT) }

    Scaffold(
        containerColor = BgDeep,
        bottomBar = {
            NavigationBar(containerColor = BgElevated) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, t.label) },
                        label = { Text(t.label, color = TextMuted) }
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