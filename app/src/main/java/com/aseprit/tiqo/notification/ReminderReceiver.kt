package com.aseprit.tiqo.notification

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.aseprit.tiqo.MainActivity
import com.aseprit.tiqo.R
import com.aseprit.tiqo.TiqoApplication
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, 0L)
        val occurrenceDate = intent.getStringExtra(EXTRA_OCCURRENCE_DATE) ?: return
        val title = intent.getStringExtra(EXTRA_ITEM_TITLE) ?: context.getString(R.string.app_name)

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED || android.os.Build.VERSION.SDK_INT < 33
        ) {
            context.getSystemService(NotificationManager::class.java).notify(
                itemId.hashCode(),
                createNotification(context, itemId, occurrenceDate, title),
            )
        }

        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                val app = context.applicationContext as TiqoApplication
                app.container.plannerRepository.getItem(itemId)?.let { item ->
                    app.container.reminderScheduler.schedule(item)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun createNotification(
        context: Context,
        itemId: Long,
        occurrenceDate: String,
        title: String,
    ): android.app.Notification {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            data = Uri.parse("tiqo://item/$itemId/$occurrenceDate")
            putExtra(EXTRA_ITEM_ID, itemId)
            putExtra(EXTRA_OCCURRENCE_DATE, occurrenceDate)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            itemId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val doneIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_DONE
            data = Uri.parse("tiqo://done/$itemId/$occurrenceDate")
            putExtra(EXTRA_ITEM_ID, itemId)
            putExtra(EXTRA_OCCURRENCE_DATE, occurrenceDate)
        }
        val donePendingIntent = PendingIntent.getBroadcast(
            context,
            (itemId.toString() + occurrenceDate).hashCode(),
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notification_reminder_text))
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, context.getString(R.string.notification_done), donePendingIntent)
            .build()
    }

    private companion object {
        val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DONE) return
        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, 0L)
        val date = intent.getStringExtra(EXTRA_OCCURRENCE_DATE)?.let(LocalDate::parse) ?: return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as TiqoApplication
                app.container.plannerRepository.setCompleted(itemId, date, true)
                context.getSystemService(NotificationManager::class.java).cancel(itemId.hashCode())
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_DONE = "com.aseprit.tiqo.action.DONE"
    }
}

