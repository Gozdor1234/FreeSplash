package com.gozdor.freesplash.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Photo(
    val id: String,
    val width: Int = 0,
    val height: Int = 0,
    val color: String? = null,
    val description: String? = null,
    @SerialName("alt_description") val altDescription: String? = null,
    val urls: Urls = Urls(),
    val links: Links = Links(),
    val user: User = User(),
    // Paywall flags. Neither is in Unsplash's public API docs; the site's own
    // internal API returns them. Default null so missing fields don't break parsing.
    val plus: Boolean? = null,
    val premium: Boolean? = null,
)

@Serializable
data class Urls(
    val raw: String? = null,
    val full: String? = null,
    val regular: String? = null,
    val small: String? = null,
    val thumb: String? = null,
)

@Serializable
data class Links(
    val html: String? = null,
    val download: String? = null,
    @SerialName("download_location") val downloadLocation: String? = null,
)

@Serializable
data class User(
    val name: String? = null,
    val username: String? = null,
    val links: UserLinks = UserLinks(),
)

@Serializable
data class UserLinks(val html: String? = null)

@Serializable
data class SearchResponse(
    val total: Int = 0,
    @SerialName("total_pages") val totalPages: Int = 0,
    val results: List<Photo> = emptyList(),
)

@Serializable
data class DownloadTrackResponse(val url: String? = null)

/**
 * A photo is shown only if it passes BOTH independent checks:
 *  1. Neither paywall flag is true.
 *  2. No image URL points at the Unsplash+ host (plus.unsplash.com), which is
 *     where watermarked premium images are served from.
 * Check 2 still works if Unsplash renames or drops the flags.
 */
fun Photo.isFree(): Boolean {
    if (plus == true || premium == true) return false
    val all = listOf(urls.raw, urls.full, urls.regular, urls.small, urls.thumb)
    return all.none { it?.contains("plus.unsplash.com", ignoreCase = true) == true }
}

const val UTM = "utm_source=freesplash&utm_medium=referral"

fun String.withUtm(): String = if (contains("?")) "$this&$UTM" else "$this?$UTM"
