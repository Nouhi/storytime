package com.storytime.app

import android.app.Application
import com.storytime.app.audio.AmbientSoundManager
import com.storytime.app.data.PreferencesManager
import com.storytime.app.network.ApiClient

class StorytimeApp : Application() {

    lateinit var preferencesManager: PreferencesManager
        private set

    lateinit var audioManager: AmbientSoundManager
        private set

    override fun onCreate() {
        super.onCreate()
        preferencesManager = PreferencesManager(this)
        audioManager = AmbientSoundManager()
        ApiClient.init(preferencesManager)
    }
}
