package com.aseprit.tiqo

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.aseprit.tiqo.notification.REMINDER_CHANNEL_ID

class TiqoApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        val channel = NotificationChannel(
            REMINDER_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.notification_channel_description)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

