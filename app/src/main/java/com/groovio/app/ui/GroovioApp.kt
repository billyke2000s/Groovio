package com.groovio.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.groovio.app.playback.PlayerViewModel
import com.groovio.app.ui.home.HomeScreen
import com.groovio.app.ui.player.MiniPlayer
import com.groovio.app.ui.player.PlayerScreen
import com.groovio.app.ui.player.QueueScreen
import com.groovio.app.ui.search.SearchScreen
import com.groovio.app.ui.search.SearchViewModel
import com.groovio.app.ui.theme.GroovioColors

private object Routes {
    const val TABS = "tabs"
    const val PLAYER = "player"
    const val QUEUE = "queue"
}

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    SEARCH("Search", Icons.Default.Search),
}

@Composable
fun GroovioApp(
    vm: PlayerViewModel = viewModel(),
    searchVm: SearchViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val position by vm.positionMs.collectAsStateWithLifecycle(initialValue = 0L)
    val nav = rememberNavController()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.playFiles(uris)
    }

    NavHost(
        navController = nav,
        startDestination = Routes.TABS,
        modifier = Modifier.fillMaxSize().background(GroovioColors.Background),
    ) {
        composable(Routes.TABS) {
            var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) {
                    when (tab) {
                        Tab.HOME -> HomeScreen(
                            onOpenFiles = { picker.launch(arrayOf("audio/*")) },
                            onSearch = { tab = Tab.SEARCH },
                        )
                        Tab.SEARCH -> SearchScreen(
                            vm = searchVm,
                            currentMediaId = state.currentMediaId,
                            onPlay = vm::playTracks,
                        )
                    }
                }
                if (state.hasItem) {
                    MiniPlayer(
                        state = state,
                        positionMs = position,
                        onOpen = { nav.navigate(Routes.PLAYER) { launchSingleTop = true } },
                        onTogglePlay = vm::togglePlay,
                        onNext = vm::next,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                NavigationBar(containerColor = GroovioColors.Background) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = GroovioColors.Text,
                                selectedTextColor = GroovioColors.Text,
                                indicatorColor = GroovioColors.SurfaceHigh,
                                unselectedIconColor = GroovioColors.TextMuted,
                                unselectedTextColor = GroovioColors.TextMuted,
                            ),
                        )
                    }
                }
            }
        }
        composable(
            Routes.PLAYER,
            enterTransition = { slideInVertically { it } },
            popExitTransition = { slideOutVertically { it } },
        ) {
            // If the queue is emptied while the player is open, go back.
            LaunchedEffect(state.connected, state.hasItem) {
                if (state.connected && !state.hasItem) nav.popBackStack(Routes.TABS, inclusive = false)
            }
            PlayerScreen(
                state = state,
                positionMs = position,
                onCollapse = { nav.popBackStack() },
                onOpenQueue = { nav.navigate(Routes.QUEUE) { launchSingleTop = true } },
                onTogglePlay = vm::togglePlay,
                onSeek = vm::seekTo,
                onNext = vm::next,
                onPrevious = vm::previous,
                onToggleShuffle = vm::toggleShuffle,
                onCycleRepeat = vm::cycleRepeat,
            )
        }
        composable(Routes.QUEUE) {
            QueueScreen(
                state = state,
                onBack = { nav.popBackStack() },
                onPlayAt = vm::playAt,
                onRemoveAt = vm::removeAt,
            )
        }
    }
}
