package com.jamie.blinkchat

import android.app.Application
import androidx.multidex.BuildConfig
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class BlinkChatApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for logging, only in debug builds
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.d("Timber logging initialized for BlinkChatApplication")
        }
    }
}