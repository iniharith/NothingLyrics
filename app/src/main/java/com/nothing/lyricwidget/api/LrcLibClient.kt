package com.nothing.lyricwidget.api

import com.google.gson.Gson
import com.nothing.lyricwidget.model.LrcResponse
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

object LrcLibClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    private const val BASE_URL = "https://lrclib.net/api"

    /**
     * Fetch lyrics for a track. Try the precise "get" endpoint first.
     * Fall back to search endpoint if precise match isn't found.
     */
    fun fetchLyrics(trackName: String, artistName: String, albumName: String? = null, durationSec: Double? = null): LrcResponse? {
        if (trackName.isBlank() || artistName.isBlank()) return null

        // 1. Try get precise lyrics
        val preciseResponse = fetchPrecise(trackName, artistName, albumName, durationSec)
        if (preciseResponse != null) {
            return preciseResponse
        }

        // 2. Fall back to search and take first matched result
        return searchFirst(trackName, artistName)
    }

    private fun fetchPrecise(trackName: String, artistName: String, albumName: String?, durationSec: Double?): LrcResponse? {
        val urlBuilder = "$BASE_URL/get".toHttpUrlOrNull()?.newBuilder() ?: return null
        urlBuilder.addQueryParameter("track_name", trackName)
        urlBuilder.addQueryParameter("artist_name", artistName)
        if (!albumName.isNullOrBlank()) {
            urlBuilder.addQueryParameter("album_name", albumName)
        }
        if (durationSec != null && durationSec > 0) {
            urlBuilder.addQueryParameter("duration", durationSec.toInt().toString())
        }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", "NothingLyricWidget/1.0.0 (https://github.com/opencode/NothingLyricWidget)")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string()
                    if (!bodyString.isNullOrBlank()) {
                        return gson.fromJson(bodyString, LrcResponse::class.java)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun searchFirst(trackName: String, artistName: String): LrcResponse? {
        val urlBuilder = "$BASE_URL/search".toHttpUrlOrNull()?.newBuilder() ?: return null
        urlBuilder.addQueryParameter("q", "$trackName $artistName")

        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", "NothingLyricWidget/1.0.0 (https://github.com/opencode/NothingLyricWidget)")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string()
                    if (!bodyString.isNullOrBlank()) {
                        val searchResults = gson.fromJson(bodyString, Array<LrcResponse>::class.java)
                        if (!searchResults.isNullOrEmpty()) {
                            // Find the one that matches artist and track name best
                            return searchResults.firstOrNull {
                                it.name.equals(trackName, ignoreCase = true) ||
                                it.artistName.equals(artistName, ignoreCase = true)
                            } ?: searchResults[0]
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
