package com.fdroid.cryptomonitor

import android.app.Application
import com.fdroid.cryptomonitor.scheduling.NotificationHelper
import com.fdroid.cryptomonitor.scheduling.SignalScheduler

class CryptoMonitorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
        SignalScheduler.schedule(this)
    }
}
