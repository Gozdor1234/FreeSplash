package com.gozdor.freesplash.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val feed by vm.feed.collectAsStateWithLifecycle()

            // Simple state-based navigation; saved across rotation.
            var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
            var showSettings by rememberSaveable { mutableStateOf(false) }

            FreeSplashTheme(mode = settings.theme, dynamic = settings.dynamicColor) {
                val selected = selectedId?.let { id -> feed.photos.firstOrNull { it.id == id } }
                when {
                    showSettings -> SettingsScreen(
                        settings = settings,
                        onSaveKey = vm::saveApiKey,
                        onTheme = { vm.setTheme(it) },
                        onDynamic = { vm.setDynamicColor(it) },
                        onBack = { showSettings = false },
                    )
                    selected != null -> PhotoScreen(
                        photo = selected,
                        messages = vm.messages,
                        onDownload = vm::download,
                        onWallpaper = vm::setWallpaper,
                        onBack = { selectedId = null },
                    )
                    else -> FeedScreen(
                        state = feed,
                        hasKey = settings.apiKey.isNotBlank(),
                        messages = vm.messages,
                        onSearch = vm::search,
                        onRefresh = { vm.refresh() },
                        onLoadMore = { vm.loadMore() },
                        onOpen = { selectedId = it.id },
                        onSettings = { showSettings = true },
                    )
                }
            }
        }
    }
}
