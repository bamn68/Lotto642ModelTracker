package com.gsbtechnologies.lotto642modeltracker

import android.app.Application
import com.gsbtechnologies.lotto642modeltracker.notifications.MajorWinNotifier

class LottoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MajorWinNotifier.createChannel(this)
    }
}
