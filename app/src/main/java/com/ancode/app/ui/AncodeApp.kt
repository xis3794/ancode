package com.ancode.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
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
import com.ancode.app.ui.screens.SessionScreen
import com.ancode.app.ui.screens.SettingsScreen
import com.ancode.app.ui.screens.TerminalScreen
import com.ancode.app.ui.screens.TodoPanel
import com.ancode.app.ui.theme.BgDeep
import com.ancode.app.ui.theme.BgElevated
import com.ancode.app.ui.theme.BorderDim
import com.ancode.app.ui.theme.TextMuted
import com.ancode.app.ui.theme.TextPrimary

private enum class Tab(val label: String, val icon: ImageVector) {
    SESSIONS("会话", Icons.Filled.Article),
    CHAT("对话", Icons.Filled.Chat),
    TERMINAL("终端", Icons.Filled.Terminal),
    SETTINGS("设置", Icons.Filled.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AncodeApp(viewModel: AppViewModel) {
    var tab by remember { mutableStateOf(Tab.CHAT) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val todos by viewModel.todos.collectAsState()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            Box(
                Modifier
                    .padding(top = 48.dp, bottom = 12.dp, end = 8.dp)
            ) {
                TodoPanel(
                    todos = todos,
                    onToggle = { viewModel.toggleTodo(it) },
                    onClear = { viewModel.clearSessionMessages() }
                )
            }
        }
    ) {
        Scaffold(
            containerColor = BgDeep,
            topBar = {
                // compact top bar with Do List toggle
                androidx.compose.material3.TopAppBar(
                    title = {
                        Text(
                            if (tab == Tab.TERMINAL) "终端 — ubuntu@ancode" else "ANCODE",
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleSmall
                        )
                    },
                    navigationIcon = {
                        androidx.compose.material3.IconButton(onClick = { drawerState.open() }) {
                            Icon(
                                Icons.Filled.Checklist,
                                contentDescription = "Do List",
                                tint = TextMuted
                            )
                        }
                    },
                    actions = {
                        val current = viewModel.currentSession.collectAsState().value
                        Text(
                            current?.title?.take(14) ?: "",
                            color = TextMuted,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = BgElevated
                    )
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
                    Tab.SESSIONS -> SessionScreen(viewModel)
                    Tab.CHAT -> ChatScreen(viewModel)
                    Tab.TERMINAL -> TerminalScreen(viewModel)
                    Tab.SETTINGS -> SettingsScreen(viewModel)
                }
            }
        }
    }
}