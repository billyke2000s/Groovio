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
import androidx.compose.material.icons.filled.LibraryMusic
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.groovio.app.GroovioApplication
import com.groovio.app.playback.PlayerViewModel
import com.groovio.app.ui.browse.AlbumScreen
import com.groovio.app.ui.browse.ArtistScreen
import com.groovio.app.ui.browse.Detail
import com.groovio.app.ui.home.HomeScreen
import com.groovio.app.ui.home.HomeViewModel
import com.groovio.app.data.TrackEntity
import com.groovio.app.ui.library.LibraryScreen
import com.groovio.app.ui.library.SongMenu
import com.groovio.app.ui.library.LibraryView
import com.groovio.app.ui.library.LibraryViewModel
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
    LIBRARY("Library", Icons.Default.LibraryMusic),
}

@Composable
fun GroovioApp(
    vm: PlayerViewModel = viewModel(),
    searchVm: SearchViewModel = viewModel(),
    libraryVm: LibraryViewModel = viewModel(),
    homeVm: HomeViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val position by vm.positionMs.collectAsStateWithLifecycle(initialValue = 0L)
    val isFavourite by vm.isFavourite.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val spotifyImport by vm.spotifyImport.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.playFiles(uris)
    }

    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var libraryView by rememberSaveable { mutableStateOf(LibraryView.ROOT) }
    val recent by libraryVm.recent.collectAsStateWithLifecycle()
    val liked by libraryVm.favourites.collectAsStateWithLifecycle()
    val related by homeVm.related.collectAsStateWithLifecycle()
    val discover by homeVm.discover.collectAsStateWithLifecycle()
    // Retry discovery when Home is shown again (e.g. after coming back online).
    LaunchedEffect(tab) { if (tab == Tab.HOME) homeVm.loadDiscover() }

    // Song menu (⋮ / long press), shown over everything.
    var menuTrack by remember { mutableStateOf<TrackEntity?>(null) }
    val openMenu: (TrackEntity) -> Unit = { menuTrack = it }

    // Artist/album pages stack over the current tab (back pops one level).
    val details = remember { mutableStateListOf<Detail>() }
    fun openDetail(d: Detail) {
        details.add(d)
        nav.popBackStack(Routes.TABS, inclusive = false)
    }
    fun selectTab(t: Tab) {
        details.clear()
        // Tapping Library again returns to its top level.
        if (t == Tab.LIBRARY && tab == Tab.LIBRARY) libraryView = LibraryView.ROOT
        tab = t
    }

    // A link or text shared to Groovio opens in Search.
    val app = LocalContext.current.applicationContext as GroovioApplication
    val shared by app.sharedText.collectAsStateWithLifecycle()
    LaunchedEffect(shared) {
        val text = shared ?: return@LaunchedEffect
        app.sharedText.value = null
        searchVm.onQueryChange(text)
        details.clear()
        tab = Tab.SEARCH
        nav.popBackStack(Routes.TABS, inclusive = false)
    }

    NavHost(
        navController = nav,
        startDestination = Routes.TABS,
        modifier = Modifier.fillMaxSize().background(GroovioColors.Background),
    ) {
        composable(Routes.TABS) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) {
                    when (val detail = details.lastOrNull()) {
                        is Detail.Artist -> ArtistScreen(
                            name = detail.name,
                            currentMediaId = state.currentMediaId,
                            onBack = { details.removeAt(details.lastIndex) },
                            onPlay = vm::playTracks,
                            onOpenAlbum = { openDetail(Detail.Album(it)) },
                            onMore = openMenu,
                        )
                        is Detail.Album -> AlbumScreen(
                            album = detail.album,
                            currentMediaId = state.currentMediaId,
                            onBack = { details.removeAt(details.lastIndex) },
                            onPlay = vm::playTracks,
                            onShuffle = vm::shuffleTracks,
                            onOpenArtist = { openDetail(Detail.Artist(it)) },
                            onMore = openMenu,
                        )
                        null -> when (tab) {
                        Tab.HOME -> HomeScreen(
                            recent = recent,
                            likedCount = liked.size,
                            playlists = playlists,
                            currentMediaId = state.currentMediaId,
                            onPlayRecent = vm::playStored,
                            onOpenLiked = { libraryView = LibraryView.LIKED; tab = Tab.LIBRARY },
                            onOpenPlaylist = { libraryView = LibraryView.playlist(it); tab = Tab.LIBRARY },
                            onSearch = { tab = Tab.SEARCH },
                            onOpenFiles = { picker.launch(arrayOf("audio/*")) },
                            onMore = openMenu,
                            related = related,
                            onPlayRelated = vm::playTracks,
                            discover = discover,
                            onOpenAlbum = { openDetail(Detail.Album(it)) },
                        )
                        Tab.SEARCH -> SearchScreen(
                            vm = searchVm,
                            currentMediaId = state.currentMediaId,
                            onPlay = vm::playTracks,
                            spotifyImport = spotifyImport,
                            onPlaySpotify = vm::playSpotify,
                            onOpenAlbum = { openDetail(Detail.Album(it)) },
                            onMore = openMenu,
                        )
                        Tab.LIBRARY -> LibraryScreen(
                            vm = libraryVm,
                            view = libraryView,
                            onViewChange = { libraryView = it },
                            currentMediaId = state.currentMediaId,
                            onPlay = vm::playStored,
                            onMore = openMenu,
                        )
                        }
                    }
                }
                if (state.hasItem) {
                    MiniPlayer(
                        state = state,
                        positionMs = position,
                        onOpen = { nav.navigate(Routes.PLAYER) { launchSingleTop = true } },
                        onTogglePlay = vm::togglePlay,
                        onNext = vm::next,
                        onPrevious = vm::previous,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                NavigationBar(containerColor = GroovioColors.Background) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { selectTab(t) },
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
                isFavourite = isFavourite,
                onToggleFavourite = vm::toggleFavourite,
                playlists = playlists,
                onAddToPlaylist = vm::addCurrentToPlaylist,
                onAddToNewPlaylist = vm::addCurrentToNewPlaylist,
                onOpenArtist = { openDetail(Detail.Artist(it)) },
            )
        }
        composable(Routes.QUEUE) {
            QueueScreen(
                state = state,
                onBack = { nav.popBackStack() },
                onPlayAt = vm::playAt,
                onRemoveAt = vm::removeAt,
                onMove = vm::move,
            )
        }
    }

    menuTrack?.let { track ->
        SongMenu(
            track = track,
            vm = vm,
            playlists = playlists,
            onOpenArtist = { openDetail(Detail.Artist(it)) },
            onDismiss = { menuTrack = null },
        )
    }
}
