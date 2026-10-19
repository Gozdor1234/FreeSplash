@file:OptIn(ExperimentalMaterial3Api::class)

package com.gozdor.freesplash.ui

import android.app.WallpaperManager
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gozdor.freesplash.data.AppSettings
import com.gozdor.freesplash.data.Photo
import com.gozdor.freesplash.data.Source
import com.gozdor.freesplash.data.ThemeMode
import com.gozdor.freesplash.data.withUtm
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

private fun parseColor(hex: String?): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray)

private fun Photo.ratio(): Float =
    if (width > 0 && height > 0) (width.toFloat() / height).coerceIn(0.4f, 2.5f) else 1f

// ---------------------------------------------------------------- Feed

@Composable
fun FeedScreen(
    state: FeedState,
    hasKey: Boolean,
    messages: Flow<String>,
    onSearch: (String?) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (Photo) -> Unit,
    onSettings: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { messages.collect { snackbar.showSnackbar(it) } }

    var text by rememberSaveable { mutableStateOf(state.query.orEmpty()) }
    val focus = LocalFocusManager.current
    val gridState = rememberLazyStaggeredGridState()
    val latestLoadMore by rememberUpdatedState(onLoadMore)
    val count by rememberUpdatedState(state.photos.size)

    // Infinite scroll: fetch more when within 6 items of the end.
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last -> if (count > 0 && last >= count - 6) latestLoadMore() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("FreeSplash", fontWeight = FontWeight.SemiBold)
                        Text(
                            statusLine(state, hasKey),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                placeholder = { Text("Search free photos") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (text.isNotEmpty()) IconButton(onClick = {
                        text = ""; onSearch(null); focus.clearFocus()
                    }) { Icon(Icons.Default.Close, "Clear") }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(text); focus.clearFocus() }),
            )

            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalItemSpacing = 8.dp,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.photos, key = { it.id }) { photo -> PhotoTile(photo) { onOpen(photo) } }
                    item(span = StaggeredGridItemSpan.FullLine) {
                        Footer(state, onRetry = onLoadMore)
                    }
                }
            }
        }
    }
}

private fun statusLine(s: FeedState, hasKey: Boolean): String {
    val src = when {
        s.source == null -> if (hasKey) "Official API" else "Backup source (no key)"
        s.usedFallback -> "Backup source (official API refused)"
        s.source == Source.OFFICIAL -> "Official API" + (s.rateRemaining?.let { " · $it req left" } ?: "")
        else -> "Backup source (no key)"
    }
    return "$src · ${s.hiddenTotal} Unsplash+ hidden"
}

@Composable
private fun PhotoTile(photo: Photo, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(photo.ratio())
            .clip(RoundedCornerShape(12.dp))
            .background(parseColor(photo.color))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = photo.urls.small ?: photo.urls.regular,
            contentDescription = photo.altDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000))))
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(
                photo.user.name.orEmpty(),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Footer(state: FeedState, onRetry: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        when {
            state.loading && !state.refreshing -> CircularProgressIndicator()
            state.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.error, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text("Retry") }
            }
            state.endReached && state.photos.isEmpty() -> Text("No free photos found")
            state.endReached -> Text("That's everything", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------- Photo detail

@Composable
fun PhotoScreen(
    photo: Photo,
    messages: Flow<String>,
    onDownload: (Photo) -> Unit,
    onWallpaper: (Photo, Int) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { messages.collect { snackbar.showSnackbar(it) } }
    val uri = LocalUriHandler.current
    val ctx = LocalContext.current

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val zoom = rememberTransformableState { z, pan, _ ->
        scale = (scale * z).coerceIn(1f, 5f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    var wallMenu by remember { mutableStateOf(false) }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = Color.Black) { _ ->
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = photo.urls.regular ?: photo.urls.small,
                contentDescription = photo.altDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .transformable(zoom)
                    .graphicsLayer {
                        scaleX = scale; scaleY = scale
                        translationX = offset.x; translationY = offset.y
                    },
            )

            IconButton(
                onClick = onBack,
                modifier = Modifier.statusBarsPadding().padding(8.dp)
                    .clip(RoundedCornerShape(50)).background(Color(0x66000000)),
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                // Attribution required by Unsplash API guidelines.
                Row {
                    Text("Photo by ", color = Color.White)
                    Text(
                        photo.user.name ?: "Unknown",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable {
                            photo.user.links.html?.let { uri.openUri(it.withUtm()) }
                        },
                    )
                    Text(" on ", color = Color.White)
                    Text(
                        "Unsplash",
                        color = Color.White,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable { uri.openUri("https://unsplash.com/".withUtm()) },
                    )
                }
                photo.altDescription?.let {
                    Text(
                        it.replaceFirstChar { c -> c.uppercase() },
                        color = Color(0xCCFFFFFF),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalIconButton(onClick = { onDownload(photo) }) {
                        Icon(Icons.Default.Download, "Download")
                    }
                    Box {
                        FilledTonalIconButton(onClick = { wallMenu = true }) {
                            Icon(Icons.Default.Wallpaper, "Set as wallpaper")
                        }
                        DropdownMenu(expanded = wallMenu, onDismissRequest = { wallMenu = false }) {
                            DropdownMenuItem(text = { Text("Home screen") }, onClick = {
                                wallMenu = false; onWallpaper(photo, WallpaperManager.FLAG_SYSTEM)
                            })
                            DropdownMenuItem(text = { Text("Lock screen") }, onClick = {
                                wallMenu = false; onWallpaper(photo, WallpaperManager.FLAG_LOCK)
                            })
                            DropdownMenuItem(text = { Text("Both") }, onClick = {
                                wallMenu = false
                                onWallpaper(photo, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
                            })
                        }
                    }
                    FilledTonalIconButton(onClick = {
                        val link = (photo.links.html ?: "https://unsplash.com/photos/${photo.id}").withUtm()
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, link)
                        ctx.startActivity(Intent.createChooser(send, "Share photo"))
                    }) { Icon(Icons.Default.Share, "Share") }
                    FilledTonalIconButton(onClick = {
                        uri.openUri((photo.links.html ?: "https://unsplash.com/photos/${photo.id}").withUtm())
                    }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open on Unsplash") }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Settings

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSaveKey: (String) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onDynamic: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val uri = LocalUriHandler.current
    var key by rememberSaveable(settings.apiKey) { mutableStateOf(settings.apiKey) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Section("Unsplash access key") {
                Text(
                    "With a key, the app uses the official API (50 requests/hour in demo mode). " +
                        "Without one, or if the official API refuses a request, it uses Unsplash's " +
                        "undocumented site endpoint, which can stop working without notice.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Access Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onSaveKey(key) }) { Text("Save") }
                    OutlinedButton(onClick = { uri.openUri("https://unsplash.com/oauth/applications") }) {
                        Text("Get a key")
                    }
                }
            }

            Section("Appearance") {
                val modes = ThemeMode.entries
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = settings.theme == m,
                            onClick = { onTheme(m) },
                            shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                        ) { Text(m.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Use wallpaper colors (Android 12+)", Modifier.weight(1f))
                    Switch(checked = settings.dynamicColor, onCheckedChange = onDynamic)
                }
            }

            Section("How Unsplash+ photos are hidden") {
                Text(
                    "A photo is shown only if it passes two independent checks: Unsplash's " +
                        "'plus'/'premium' flags are not true, and none of its image URLs come from " +
                        "plus.unsplash.com (where watermarked Unsplash+ images are served). " +
                        "The count of hidden photos is shown under the app title.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Surface(tonalElevation = 2.dp, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}
