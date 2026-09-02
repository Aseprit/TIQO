package com.aseprit.tiqo.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.aseprit.tiqo.domain.model.PlannerItem
import com.aseprit.tiqo.domain.recurrence.RecurrenceEngine
import com.aseprit.tiqo.domain.reminder.ReminderScheduler
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class AndroidReminderScheduler(
    private val context: Context,
    private val defaultAllDayTime: suspend () -> LocalTime,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ReminderScheduler {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override suspend fun schedule(item: PlannerItem) {
        val reminder = item.reminder ?: return
        val occurrenceTime = item.time ?: defaultAllDayTime()
        val occurrence = findNextOccurrence(item, occurrenceTime) ?: return
        val eventTime = occurrence.atTime(occurrenceTime)
            .atZone(ZoneId.systemDefault())
        val triggerAtMillis = eventTime.minusMinutes(reminder.minutesBefore.toLong()).toInstant().toEpochMilli()
        if (triggerAtMillis <= clock.millis() + MINIMUM_LEAD_MILLIS) return

        val pendingIntent = reminderPendingIntent(item, occurrence)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        }
    }

    override suspend fun cancel(itemId: Long) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            data = Uri.parse("tiqo://reminder/$itemId")
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            itemId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun findNextOccurrence(item: PlannerItem, occurrenceTime: LocalTime): LocalDate? {
        val today = LocalDate.now(clock)
        val start = maxOf(today, item.startDate)
        val horizon = start.plusYears(10)
        return generateSequence(start) { current ->
            current.plusDays(1).takeIf { it <= horizon }
        }.firstOrNull { date ->
            if (!RecurrenceEngine.occursOn(item, date)) return@firstOrNull false
            val eventTime = date.atTime(occurrenceTime).atZone(ZoneId.systemDefault())
            val trigger = eventTime.minusMinutes(item.reminder?.minutesBefore?.toLong() ?: 0)
            trigger.toInstant().toEpochMilli() > clock.millis() + MINIMUM_LEAD_MILLIS
        }
    }

    private fun reminderPendingIntent(item: PlannerItem, occurrenceDate: LocalDate): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            data = Uri.parse("tiqo://reminder/${item.id}")
            putExtra(EXTRA_ITEM_ID, item.id)
            putExtra(EXTRA_OCCURRENCE_DATE, occurrenceDate.toString())
            putExtra(EXTRA_ITEM_TITLE, item.title)
        }
        return PendingIntent.getBroadcast(
            context,
            item.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val MINIMUM_LEAD_MILLIS = 5_000L
    }
}
