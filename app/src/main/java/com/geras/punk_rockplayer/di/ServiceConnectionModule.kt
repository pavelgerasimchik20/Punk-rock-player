package com.geras.punk_rockplayer.di

import android.content.Context
import com.geras.punk_rockplayer.service.ServiceConnectionController
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class ServiceConnectionModule {
    @Singleton
    @Provides
    fun provideServiceConnection(context: Context): ServiceConnectionController {
        return ServiceConnectionController(context)
    }
}