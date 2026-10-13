package com.morningguard.app

import android.app.Application
import com.morningguard.app.di.AppInitializer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MorningGuardApplication : Application() {
    @Inject lateinit var appInitializer: AppInitializer

    override fun onCreate() {
        super.onCreate()
        appInitializer.initialize(this)
    }
}
