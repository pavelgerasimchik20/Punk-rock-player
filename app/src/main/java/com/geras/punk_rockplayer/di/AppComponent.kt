package com.geras.punk_rockplayer.di

import android.content.Context
import com.geras.punk_rockplayer.MainActivity
import com.geras.punk_rockplayer.service.PlayerService
import dagger.BindsInstance
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(modules = [DataModule::class, ServiceConnectionModule::class])
interface AppComponent {
    @Component.Factory
    interface Factory {
        fun create(@BindsInstance context: Context): AppComponent
    }

    fun inject(activity: MainActivity)
    fun inject(service: PlayerService)
}