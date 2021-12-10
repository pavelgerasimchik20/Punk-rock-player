package com.geras.punk_rockplayer.data_source

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.lang.reflect.Type
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
data class SongsFromJson @Inject constructor(private val context: Context) :
    SongsRepo {

    private val trackList: List<Song>

    private val maxIndex: Int get() = trackList.size - 1
    private var currentIndex = 0

    override val current: Song
        get() = trackList[currentIndex]

    override val next: Song
        get() {
            currentIndex = if (currentIndex == maxIndex) 0 else currentIndex.plus(1)
            return current
        }

    override val previous: Song
        get() {
            currentIndex = if (currentIndex == 0) maxIndex else currentIndex.minus(1)
            return current
        }

    init {
        trackList = assignListOfSong()
    }

    private fun assignListOfSong(): List<Song> {
        val gson = Gson()
        val json = getJsonByUsingContextAssets("playList.json")
        val listOfTrackType: Type = object : TypeToken<List<Song>>() {}.type
        return gson.fromJson(json, listOfTrackType)
    }

    private fun getJsonByUsingContextAssets(jsonFile: String): String {
        return try {
            context
                .assets
                .open(jsonFile)
                .bufferedReader()
                .use(BufferedReader::readText)
        } catch (e: Exception) {
            "[]"
        }
    }
}
