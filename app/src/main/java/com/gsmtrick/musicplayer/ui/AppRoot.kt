package com.gsmtrick.musicplayer.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import com.gsmtrick.musicplayer.ui.components.AnimatedAuroraBackground
import com.gsmtrick.musicplayer.ui.components.EdgeLightingOverlay
import com.gsmtrick.musicplayer.ui.components.GlassBackdrop
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.gsmtrick.musicplayer.playback.MusicPlaybackService
import com.gsmtrick.musicplayer.ui.screens.AboutScreen
import com.gsmtrick.musicplayer.ui.screens.AppLockScreen
import com.gsmtrick.musicplayer.ui.screens.EffectsScreen
import com.gsmtrick.musicplayer.ui.screens.LibraryScreen
import com.gsmtrick.musicplayer.ui.screens.NowPlayingSheet
import com.gsmtrick.musicplayer.ui.screens.RadioScreen
import com.gsmtrick.musicplayer.ui.screens.SettingsScreen
import com.gsmtrick.musicplayer.ui.screens.StatsScreen
import com.gsmtrick.musicplayer.ui.screens.YoutubeScreen
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.gsmtrick.musicplayer.ui.theme.ModernMusicTheme

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AppRoot(viewModel: PlayerViewModel) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val readPerm = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val notifPerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null

    val perm = rememberPermissionState(readPerm) { granted ->
        viewModel.setPermissionGranted(granted)
    }
    val notifPermState = notifPerm?.let { rememberPermissionState(it) }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, readPerm) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        viewModel.setPermissionGranted(granted)
        if (!granted) perm.launchPermissionRequest()
        notifPermState?.let {
            if (!it.status.isGranted) it.launchPermissionRequest()
        }
        // Start the playback service so MediaController can bind.
        context.startService(Intent(context, MusicPlaybackService::class.java))
    }

    ModernMusicTheme(
        themeMode = prefs.themeMode,
        dynamicColor = prefs.dynamicColor,
        accent = prefs.accent,
    ) {
        var unlocked by remember { mutableStateOf(false) }
        if (prefs.appLockPin.isNotEmpty() && !unlocked) {
            AppLockScreen(
                expectedPin = prefs.appLockPin,
                onUnlock = { unlocked = true },
            )
            return@ModernMusicTheme
        }
        val nav = rememberNavController()
        val backStack by nav.currentBackStackEntryAsState()
        val current = backStack?.destination?.route ?: "library"

        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = current == "library",
                        onClick = { nav.navigate("library") { popUpTo("library") { inclusive = true } } },
                        icon = { Icon(Icons.Rounded.LibraryMusic, null) },
                        label = { Text(androidx.compose.ui.res.stringResource(com.gsmtrick.musicplayer.R.string.nav_library)) },
                    )
                    NavigationBarItem(
                        selected = current == "youtube",
                        onClick = { nav.navigate("youtube") },
                        icon = { Icon(Icons.Rounded.Search, null) },
                        label = { Text(androidx.compose.ui.res.stringResource(com.gsmtrick.musicplayer.R.string.nav_youtube)) },
                    )
                    NavigationBarItem(
                        selected = current == "effects",
                        onClick = { nav.navigate("effects") },
                        icon = { Icon(Icons.Rounded.Equalizer, null) },
                        label = { Text(androidx.compose.ui.res.stringResource(com.gsmtrick.musicplayer.R.string.nav_effects)) },
                    )
                    NavigationBarItem(
                        selected = current == "settings",
                        onClick = { nav.navigate("settings") },
                        icon = { Icon(Icons.Rounded.Settings, null) },
                        label = { Text(androidx.compose.ui.res.stringResource(com.gsmtrick.musicplayer.R.string.nav_settings)) },
                    )
                    NavigationBarItem(
                        selected = current == "about",
                        onClick = { nav.navigate("about") },
                        icon = { Icon(Icons.Rounded.Info, null) },
                        label = { Text(androidx.compose.ui.res.stringResource(com.gsmtrick.musicplayer.R.string.nav_about)) },
                    )
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (prefs.glassTheme) {
                    GlassBackdrop(modifier = Modifier.fillMaxSize())
                }
                if (prefs.animatedWallpaper) {
                    AnimatedAuroraBackground(
                        modifier = Modifier.fillMaxSize(),
                        playing = state.isPlaying,
                    )
                }
                NavHost(navController = nav, startDestination = "library") {
                    composable("library") { LibraryScreen(viewModel) }
                    composable("youtube") { YoutubeScreen(viewModel) }
                    composable("effects") { EffectsScreen(viewModel) }
                    composable("settings") {
                        SettingsScreen(
                            viewModel,
                            onOpenStats = { nav.navigate("stats") },
                            onOpenRadio = { nav.navigate("radio") },
                        )
                    }
                    composable("about") { AboutScreen() }
                    composable("stats") { StatsScreen(viewModel, onBack = { nav.popBackStack() }) }
                    composable("radio") { RadioScreen(viewModel) }
                }
                if (state.currentSong != null) {
                    NowPlayingSheet(viewModel = viewModel)
                }
                if (prefs.edgeLighting && state.isPlaying) {
                    EdgeLightingOverlay(
                        modifier = Modifier.fillMaxSize(),
                        active = true,
                    )
                }
            }
        }
    }
}


