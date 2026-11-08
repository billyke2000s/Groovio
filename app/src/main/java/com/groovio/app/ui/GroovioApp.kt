package com.groovio.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.groovio.app.ui.theme.GroovioColors

private object Routes {
    const val HOME = "home"
    const val PLAYER = "player"
    const val QUEUE = "queue"
}

@Composable
fun GroovioApp(vm: PlayerViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val position by vm.positionMs.collectAsStateWithLifecycle(initialValue = 0L)
    val nav = rememberNavController()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.playFiles(uris)
    }

    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        modifier = Modifier.fillMaxSize().background(GroovioColors.Background),
    ) {
        composable(Routes.HOME) {
            Box(Modifier.fillMaxSize()) {
                HomeScreen(
                    onOpenFiles = { picker.launch(arrayOf("audio/*")) },
                    modifier = Modifier.statusBarsPadding(),
                )
                if (state.hasItem) {
                    MiniPlayer(
                        state = state,
                        positionMs = position,
                        onOpen = { nav.navigate(Routes.PLAYER) { launchSingleTop = true } },
                        onTogglePlay = vm::togglePlay,
                        onNext = vm::next,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(8.dp),
                    )
                }
            }
        }
        composable(
            Routes.PLAYER,
            enterTransition = { slideInVertically { it } },
            popExitTransition = { slideOutVertically { it } },
        ) {
            // If the queue is emptied while the player is open, go back home.
            LaunchedEffect(state.connected, state.hasItem) {
                if (state.connected && !state.hasItem) nav.popBackStack(Routes.HOME, inclusive = false)
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
