package com.gozdor.freesplash.ui

import android.app.Application
import android.app.DownloadManager
import android.app.WallpaperManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gozdor.freesplash.data.AppSettings
import com.gozdor.freesplash.data.Photo
import com.gozdor.freesplash.data.SettingsStore
import com.gozdor.freesplash.data.Source
import com.gozdor.freesplash.data.ThemeMode
import com.gozdor.freesplash.data.UnsplashRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeedState(
    val photos: List<Photo> = emptyList(),
    val query: String? = null,
    val nextPage: Int = 1,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
    val hiddenTotal: Int = 0,
    val source: Source? = null,
    val usedFallback: Boolean = false,
    val rateRemaining: Int? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = UnsplashRepository()
    private val store = SettingsStore(app)

    val settings: StateFlow<AppSettings> =
        store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _feed = MutableStateFlow(FeedState())
    val feed: StateFlow<FeedState> = _feed.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var loadJob: Job? = null
    private var generation = 0

    init {
        viewModelScope.launch {
            // Wait for stored settings before the first request so a saved key is used.
            loadMore(store.settings.first().apiKey)
        }
    }

    fun search(query: String?) {
        val q = query?.trim()?.takeIf { it.isNotEmpty() }
        loadJob?.cancel()
        generation++
        _feed.value = FeedState(query = q)
        loadMore()
    }

    fun refresh(keyOverride: String? = null) {
        loadJob?.cancel()
        generation++
        val q = _feed.value.query
        _feed.value = FeedState(query = q, refreshing = true)
        loadMore(keyOverride)
    }

    fun loadMore(keyOverride: String? = null) {
        val s = _feed.value
        if (s.loading || s.endReached) return
        val apiKey = keyOverride ?: settings.value.apiKey
        _feed.update { it.copy(loading = true, error = null) }
        val myGen = generation

        loadJob = viewModelScope.launch {
            var added = 0
            var chained = 0
            try {
                // Filtering can leave a page nearly empty, so pull up to 3 pages
                // per scroll trigger to keep the grid filling. Capped to protect
                // the 50/hr demo-key limit.
                while (true) {
                    val cur = _feed.value
                    val result = repo.loadPage(cur.query, cur.nextPage, apiKey)
                    val existing = cur.photos.mapTo(HashSet()) { it.id }
                    val fresh = result.photos.filter { it.id !in existing }
                    added += fresh.size
                    chained++
                    _feed.update {
                        it.copy(
                            photos = it.photos + fresh,
                            nextPage = it.nextPage + 1,
                            endReached = result.rawCount == 0,
                            hiddenTotal = it.hiddenTotal + result.hidden,
                            source = result.source,
                            usedFallback = result.usedFallback,
                            rateRemaining = result.rateRemaining ?: it.rateRemaining,
                        )
                    }
                    if (result.rawCount == 0 || added >= 10 || chained >= 3) break
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _feed.update { it.copy(error = e.message ?: "Couldn't load photos") }
            } finally {
                // A cancelled older load must not clear the flags of a newer one.
                if (myGen == generation) {
                    _feed.update { it.copy(loading = false, refreshing = false) }
                }
            }
        }
    }

    fun download(photo: Photo) {
        val url = photo.urls.full ?: photo.urls.regular ?: return
        viewModelScope.launch {
            repo.trackDownload(photo, settings.value.apiKey)
            val dm = getApplication<Application>().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val name = photo.user.name ?: "Unknown"
            val req = DownloadManager.Request(Uri.parse(url))
                .setTitle("Photo by $name")
                .setMimeType("image/jpeg")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_PICTURES, "FreeSplash/${photo.id}.jpg"
                )
            dm.enqueue(req)
            _messages.send("Downloading to Pictures/FreeSplash")
        }
    }

    fun setWallpaper(photo: Photo, which: Int) {
        viewModelScope.launch {
            try {
                _messages.send("Setting wallpaper...")
                val ctx = getApplication<Application>()
                val width = ctx.resources.displayMetrics.heightPixels.coerceAtLeast(1920)
                val bytes = repo.wallpaperBytes(photo, width)
                repo.trackDownload(photo, settings.value.apiKey)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    WallpaperManager.getInstance(ctx).setStream(bytes.inputStream(), null, true, which)
                }
                _messages.send("Wallpaper set")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _messages.send("Wallpaper failed: ${e.message}")
            }
        }
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            store.setApiKey(key)
            refresh(key.trim())
        }
    }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { store.setTheme(mode) }
    fun setDynamicColor(on: Boolean) = viewModelScope.launch { store.setDynamicColor(on) }
}
