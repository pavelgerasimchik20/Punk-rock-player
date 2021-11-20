package com.geras.punk_rockplayer.di

import com.geras.punk_rockplayer.data_source.SongsFromJson
import com.geras.punk_rockplayer.data_source.SongsRepo
import dagger.Binds
import dagger.Module

@Module
abstract class DataModule {
    @Binds
    abstract fun provideTracks(tracks: SongsFromJson): SongsRepo
}