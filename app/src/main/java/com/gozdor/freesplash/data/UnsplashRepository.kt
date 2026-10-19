package com.gozdor.freesplash.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

enum class Source { OFFICIAL, INTERNAL }

class ApiException(val code: Int, message: String) : Exception(message)

data class PageResult(
    val photos: List<Photo>,   // already filtered to free photos only
    val rawCount: Int,         // how many the server returned before filtering
    val hidden: Int,           // how many were dropped as Unsplash+
    val source: Source,
    val usedFallback: Boolean, // true if official failed and internal was used
    val rateRemaining: Int?,   // X-Ratelimit-Remaining (official only)
)

class UnsplashRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    private val officialBase = "https://api.unsplash.com"
    private val internalBase = "https://unsplash.com/napi"

    /** query == null -> editorial feed; otherwise a search. */
    suspend fun loadPage(query: String?, page: Int, apiKey: String): PageResult {
        if (apiKey.isBlank()) return fetch(Source.INTERNAL, query, page, apiKey, false)
        return try {
            fetch(Source.OFFICIAL, query, page, apiKey, false)
        } catch (e: ApiException) {
            // 401 bad key, 403 rate limit exceeded, 429 too many requests.
            if (e.code == 401 || e.code == 403 || e.code == 429) {
                fetch(Source.INTERNAL, query, page, apiKey, true)
            } else throw e
        }
    }

    private suspend fun fetch(
        source: Source, query: String?, page: Int, apiKey: String, fallback: Boolean,
    ): PageResult = withContext(Dispatchers.IO) {
        val base = if (source == Source.OFFICIAL) officialBase else internalBase
        val url: HttpUrl = (if (query.isNullOrBlank()) "$base/photos" else "$base/search/photos")
            .toHttpUrl().newBuilder().apply {
                if (!query.isNullOrBlank()) addQueryParameter("query", query)
                addQueryParameter("page", page.toString())
                addQueryParameter("per_page", "30")
            }.build()

        val req = Request.Builder().url(url).header("Accept", "application/json").apply {
            if (source == Source.OFFICIAL) {
                header("Authorization", "Client-ID $apiKey")
                header("Accept-Version", "v1")
            }
        }.build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw ApiException(resp.code, "${source.name.lowercase()} source: HTTP ${resp.code}")
            }
            val raw: List<Photo> = if (query.isNullOrBlank()) {
                json.decodeFromString<List<Photo>>(body)
            } else {
                json.decodeFromString<SearchResponse>(body).results
            }
            val free = raw.filter { it.isFree() }
            PageResult(
                photos = free,
                rawCount = raw.size,
                hidden = raw.size - free.size,
                source = source,
                usedFallback = fallback,
                rateRemaining = resp.header("X-Ratelimit-Remaining")?.toIntOrNull(),
            )
        }
    }

    /**
     * Unsplash API guidelines require pinging download_location whenever the app
     * downloads a photo or sets it as a wallpaper. Only possible with a key.
     * Failures are ignored; they must never block the user's download.
     */
    suspend fun trackDownload(photo: Photo, apiKey: String) {
        val loc = photo.links.downloadLocation ?: return
        if (apiKey.isBlank()) return
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder().url(loc)
                    .header("Authorization", "Client-ID $apiKey")
                    .header("Accept-Version", "v1")
                    .build()
                client.newCall(req).execute().close()
            }
        }
    }

    /** Wallpaper-sized JPEG bytes. Built from urls.raw so the ixid parameter is kept. */
    suspend fun wallpaperBytes(photo: Photo, widthPx: Int): ByteArray = withContext(Dispatchers.IO) {
        val src = photo.urls.raw?.let { "$it&w=$widthPx&q=85&fm=jpg" }
            ?: photo.urls.full ?: photo.urls.regular
            ?: throw IllegalStateException("No image URL")
        client.newCall(Request.Builder().url(src).build()).execute().use { r ->
            if (!r.isSuccessful) throw ApiException(r.code, "Image download failed: HTTP ${r.code}")
            r.body?.bytes() ?: throw IllegalStateException("Empty image")
        }
    }
}
