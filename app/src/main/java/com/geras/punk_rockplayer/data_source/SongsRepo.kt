package com.geras.punk_rockplayer.data_source

interface SongsRepo {

    val previous: Song
    val current: Song
    val next: Song
}