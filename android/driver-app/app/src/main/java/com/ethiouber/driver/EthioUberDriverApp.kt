package com.ethiouber.driver

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class EthioUberDriverApp : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
