package com.geras.punk_rockplayer

import android.app.Application
import com.geras.punk_rockplayer.di.AppComponent
import com.geras.punk_rockplayer.di.DaggerAppComponent

class PlayerApplication : Application() {
    val appComponent: AppComponent by lazy {
        DaggerAppComponent.factory().create(this)
    }
}
