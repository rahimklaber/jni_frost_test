package me.rahimklaber.frosttestapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FrostApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}