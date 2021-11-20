package com.geras.punk_rockplayer

import android.app.Application

class PlayerApplication : Application() {
    val appComponent: AppComponent by lazy {
        DaggerAppComponent.factory().create(this)
    }
}
