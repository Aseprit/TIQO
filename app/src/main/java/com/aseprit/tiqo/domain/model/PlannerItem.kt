package com.aseprit.tiqo.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

data class PlannerItem(
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val type: PlannerItemType = PlannerItemType.Task,
    val startDate: LocalDate,
    val time: LocalTime? = null,
    val isAllDay: Boolean = time == null,
    val reminder: ReminderOffset? = null,
    val recurrence: RecurrenceRule = RecurrenceRule.None,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

enum class PlannerItemType {
    Task,
    Event,
    Birthday,
}

enum class ReminderOffset(val minutesBefore: Int) {
    AtTime(0),
    FiveMinutes(5),
    TenMinutes(10),
    ThirtyMinutes(30),
    OneHour(60),
    OneDay(24 * 60),
}

sealed interface RecurrenceRule {
    val interval: Int
    val endDate: LocalDate?

    data object None : RecurrenceRule {
        override val interval: Int = 1
        override val endDate: LocalDate? = null
    }

    data class Daily(
        override val interval: Int = 1,
        override val endDate: LocalDate? = null,
    ) : RecurrenceRule

    data class Weekly(
        val days: Set<DayOfWeek>,
        override val interval: Int = 1,
        override val endDate: LocalDate? = null,
    ) : RecurrenceRule

    data class Monthly(
        override val interval: Int = 1,
        override val endDate: LocalDate? = null,
    ) : RecurrenceRule

    data class Yearly(
        override val interval: Int = 1,
        override val endDate: LocalDate? = null,
    ) : RecurrenceRule
}

data class PlannerOccurrence(
    val item: PlannerItem,
    val date: LocalDate,
    val isCompleted: Boolean,
    val completedAtMillis: Long? = null,
) {
    val stableKey: String = "${item.id}:$date"
}

data class DailyCompletion(
    val date: LocalDate,
    val completed: Int,
    val scheduled: Int,
)

data class WeeklyStatistics(
    val completed: Int,
    val scheduled: Int,
    val days: List<DailyCompletion>,
) {
    val completionRate: Int = if (scheduled == 0) 0 else (completed * 100f / scheduled).toInt()
}

