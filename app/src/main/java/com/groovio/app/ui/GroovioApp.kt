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
import androidx.compose.material.icons.filled.Add
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.groovio.app.BuildConfig
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
import com.groovio.app.ui.settings.SettingsScreen
import com.groovio.app.ui.search.SearchViewModel
import com.groovio.app.ui.theme.GroovioColors

private object Routes {
    const val TABS = "tabs"
    const val PLAYER = "player"
    const val QUEUE = "queue"
    const val SETTINGS = "settings"
    const val DOWNLOADS = "downloads"
    const val SEARCH = "full_search"
}

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    SEARCH("Search", Icons.Default.Search),
    LIBRARY("Your Library", Icons.Default.LibraryMusic),
    CREATE("Create", Icons.Default.Add),
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
    val sleepTimer by vm.sleepTimer.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.playFiles(uris)
    }

    var homeFilter by rememberSaveable { mutableStateOf("All") }
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var creatingPlaylist by rememberSaveable { mutableStateOf(false) }
    var libraryView by rememberSaveable { mutableStateOf(LibraryView.ROOT) }
    val recent by libraryVm.recent.collectAsStateWithLifecycle()
    val liked by libraryVm.favourites.collectAsStateWithLifecycle()
    val related by homeVm.related.collectAsStateWithLifecycle()
    val discover by homeVm.discover.collectAsStateWithLifecycle()
    val chart by homeVm.chart.collectAsStateWithLifecycle()
    val personalMixes by homeVm.personalMixes.collectAsStateWithLifecycle()
    val mixNotice by homeVm.mixNotice.collectAsStateWithLifecycle()
    val mix by homeVm.mix.collectAsStateWithLifecycle()
    val update by homeVm.update.collectAsStateWithLifecycle()
    val updateMessage by homeVm.updateMessage.collectAsStateWithLifecycle()
    // Check for updates whenever Groovio comes back to the foreground.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { homeVm.onResume() }
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
        if (t == Tab.CREATE) { creatingPlaylist = true; return }
        if (t == Tab.SEARCH) { nav.navigate(Routes.SEARCH) { launchSingleTop = true }; return }
        details.clear()
        // Tapping Library again returns to its top level.
        if (t == Tab.LIBRARY && tab == Tab.LIBRARY) libraryView = LibraryView.ROOT
        tab = t
    }

    // A link or text shared to Groovio opens in Search.
    val app = LocalContext.current.applicationContext as GroovioApplication
    val online by app.connectivity.online.collectAsStateWithLifecycle()
    val offlineOnly by app.settings.offlineOnly.collectAsStateWithLifecycle()
    val downloadsEnabled by app.settings.downloadsEnabled.collectAsStateWithLifecycle()
    val downloaded by app.downloads.tracks.collectAsStateWithLifecycle()
    val notice by app.playbackNotice.collectAsStateWithLifecycle()
    val offline = !online || offlineOnly
    LaunchedEffect(online, offlineOnly) { if (online && !offlineOnly) homeVm.loadDiscover() }
    val shared by app.sharedText.collectAsStateWithLifecycle()
    LaunchedEffect(shared) {
        val text = shared ?: return@LaunchedEffect
        app.sharedText.value = null
        searchVm.onQueryChange(text)
        details.clear()
        nav.popBackStack(Routes.TABS, inclusive = false)
        nav.navigate(Routes.SEARCH) { launchSingleTop = true }
    }

    NavHost(
        navController = nav,
        startDestination = Routes.TABS,
        modifier = Modifier.fillMaxSize().background(GroovioColors.Background),
    ) {
        composable(Routes.TABS) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                if (offline) {
                    androidx.compose.material3.TextButton(
                        onClick = { nav.navigate(Routes.DOWNLOADS) { launchSingleTop = true } },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(com.groovio.app.ui.library.offlineMessage(downloaded.size))
                    }
                }
                notice?.let { Text(it, modifier = Modifier.padding(horizontal = 20.dp)) }
                Box(Modifier.weight(1f).fillMaxWidth()) {
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
                        Tab.HOME -> if (homeFilter == "Podcasts" || homeFilter == "Audiobooks") {
                            Column {
                                androidx.compose.foundation.lazy.LazyRow(
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                                ) {
                                    items(4) { index ->
                                        val label = listOf("All", "Music", "Podcasts", "Audiobooks")[index]
                                        androidx.compose.material3.FilterChip(selected = homeFilter == label, onClick = { homeFilter = label },
                                            label = { Text(label) }, colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = GroovioColors.Accent, selectedLabelColor = GroovioColors.OnAccent))
                                    }
                                }
                                com.groovio.app.spoken.SpokenScreen(homeFilter, vm::playTracks, vm::downloadOnline)
                            }
                        } else HomeScreen(
                            filter = homeFilter, onFilter = { homeFilter = it },
                            recent = recent,
                            likedCount = liked.size,
                            playlists = playlists,
                            currentMediaId = state.currentMediaId,
                            onPlayRecent = vm::playStored,
                            onOpenLiked = { libraryView = LibraryView.LIKED; tab = Tab.LIBRARY },
                            onOpenPlaylist = { libraryView = LibraryView.playlist(it); tab = Tab.LIBRARY },
                            onSearch = { nav.navigate(Routes.SEARCH) { launchSingleTop = true } },
                            onOpenFiles = { picker.launch(arrayOf("audio/*")) },
                            onMore = openMenu,
                            chart = if (offline) null else chart,
                            onPlayChart = vm::playSpotify,
                            onDownloadChart = { vm.playSpotify(it, true) },
                            downloadsEnabled = downloadsEnabled,
                            personalMixes = personalMixes, onSaveMix = homeVm::saveMix, mixNotice = mixNotice,
                            mix = if (offline) null else mix,
                            related = if (offline) null else related,
                            onPlayRelated = vm::playTracks,
                            discover = if (offline) emptyList() else discover,
                            update = update,
                            onUpdate = homeVm::startUpdate,
                            onOpenSettings = { nav.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                            onOpenAlbum = { openDetail(Detail.Album(it)) },
                        )
                        Tab.SEARCH, Tab.CREATE -> SearchScreen(
                            vm = searchVm,
                            currentMediaId = state.currentMediaId,
                            onPlay = vm::playTracks,
                            spotifyImport = spotifyImport,
                            onPlaySpotify = vm::playSpotify,
                            onDownload = { tracks, index -> vm.downloadOnline(tracks, index) },
                            onDownloadSpotify = { vm.playSpotify(it, true) },
                            downloadsEnabled = downloadsEnabled && !offline,
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
                            onOpenDownloads = { nav.navigate(Routes.DOWNLOADS) },
                            onOpenSpoken = { homeFilter = it; tab = Tab.HOME },
                            onOpenArtist = { openDetail(Detail.Artist(it)) },
                            versionName = BuildConfig.VERSION_NAME,
                            updateMessage = updateMessage,
                            onCheckUpdates = { homeVm.checkForUpdates(manual = true) },
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
            Routes.SEARCH,
            enterTransition = { slideInVertically { it } },
            popExitTransition = { slideOutVertically { it } },
        ) {
            androidx.activity.compose.BackHandler { nav.popBackStack() }
            SearchScreen(
                vm = searchVm, currentMediaId = state.currentMediaId,
                onPlay = vm::playTracks, spotifyImport = spotifyImport,
                onPlaySpotify = vm::playSpotify,
                onDownload = { tracks, index -> vm.downloadOnline(tracks, index) },
                onDownloadSpotify = { vm.playSpotify(it, true) },
                downloadsEnabled = downloadsEnabled && !offline,
                onOpenAlbum = { openDetail(Detail.Album(it)) }, onMore = openMenu,
                recent = recent, onPlayRecent = vm::playStored,
                onBack = { nav.popBackStack() },
                modifier = Modifier.statusBarsPadding(),
            )
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
                sleepTimer = sleepTimer,
                onSetSleepTimer = vm::setSleepTimer,
            )
        }
        composable(Routes.DOWNLOADS) {
            com.groovio.app.ui.library.DownloadsScreen(onBack = { nav.popBackStack() }, onPlay = vm::playStored)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                versionName = BuildConfig.VERSION_NAME,
                updateMessage = updateMessage,
                onCheckUpdates = { homeVm.checkForUpdates(manual = true) },
                onBack = { nav.popBackStack() },
                onOpenDownloads = { nav.navigate(Routes.DOWNLOADS) { launchSingleTop = true } },
                onSleepTimer = vm::setSleepTimer,
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

    if (creatingPlaylist) com.groovio.app.ui.library.PlaylistNameDialog(
        title = "Create playlist", confirmLabel = "Create",
        onConfirm = { libraryVm.createPlaylist(it); tab = Tab.LIBRARY; libraryView = LibraryView.ROOT },
        onDismiss = { creatingPlaylist = false },
    )

    var dismissedUpdate by rememberSaveable { mutableStateOf<String?>(null) }
    update?.takeIf { it.update.version != dismissedUpdate }?.let { available ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { dismissedUpdate = available.update.version },
            title = { Text("Groovio update") },
            text = { Column {
                Text(available.update.version)
                when (available) {
                    is com.groovio.app.ui.home.UpdateState.Downloading -> {
                        Text("Downloading…")
                        androidx.compose.material3.LinearProgressIndicator(progress = { available.progress.coerceIn(0f, 1f) })
                    }
                    is com.groovio.app.ui.home.UpdateState.Failed -> Text(available.message)
                    is com.groovio.app.ui.home.UpdateState.NeedsPermission -> Text("Allow installation, then return to Groovio.")
                    else -> Text("Install the latest build while keeping your library.")
                }
            } },
            confirmButton = { if (available !is com.groovio.app.ui.home.UpdateState.Downloading)
                androidx.compose.material3.TextButton(onClick = homeVm::startUpdate) { Text(
                    if (available is com.groovio.app.ui.home.UpdateState.Failed) "Retry" else "Install update") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { dismissedUpdate = available.update.version }) { Text("Later") } },
        )
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
