package com.aseprit.tiqo.data.database

import com.aseprit.tiqo.domain.model.PlannerItem
import com.aseprit.tiqo.domain.model.PlannerItemType
import com.aseprit.tiqo.domain.model.RecurrenceRule
import com.aseprit.tiqo.domain.model.ReminderOffset
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

internal fun PlannerItem.toEntity(idOverride: Long = id): PlannerItemEntity {
    val recurrenceType: String
    val recurrenceInterval: Int
    val weekdaysMask: Int
    val recurrenceEnd: LocalDate?

    when (val rule = recurrence) {
        RecurrenceRule.None -> {
            recurrenceType = "NONE"
            recurrenceInterval = 1
            weekdaysMask = 0
            recurrenceEnd = null
        }
        is RecurrenceRule.Daily -> {
            recurrenceType = "DAILY"
            recurrenceInterval = rule.interval
            weekdaysMask = 0
            recurrenceEnd = rule.endDate
        }
        is RecurrenceRule.Weekly -> {
            recurrenceType = "WEEKLY"
            recurrenceInterval = rule.interval
            weekdaysMask = rule.days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }
            recurrenceEnd = rule.endDate
        }
        is RecurrenceRule.Monthly -> {
            recurrenceType = "MONTHLY"
            recurrenceInterval = rule.interval
            weekdaysMask = 0
            recurrenceEnd = rule.endDate
        }
        is RecurrenceRule.Yearly -> {
            recurrenceType = "YEARLY"
            recurrenceInterval = rule.interval
            weekdaysMask = 0
            recurrenceEnd = rule.endDate
        }
    }

    return PlannerItemEntity(
        id = idOverride,
        title = title,
        description = description,
        type = type.name.uppercase(),
        startDateEpochDay = startDate.toEpochDay(),
        timeMinutes = time?.let { it.hour * 60 + it.minute },
        isAllDay = isAllDay,
        reminderMinutesBefore = reminder?.minutesBefore,
        recurrenceType = recurrenceType,
        recurrenceInterval = recurrenceInterval.coerceAtLeast(1),
        recurrenceWeekdaysMask = weekdaysMask,
        recurrenceEndEpochDay = recurrenceEnd?.toEpochDay(),
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )
}

internal fun PlannerItemEntity.toDomain(): PlannerItem {
    val startDate = LocalDate.ofEpochDay(startDateEpochDay)
    val endDate = recurrenceEndEpochDay?.let(LocalDate::ofEpochDay)
    val interval = recurrenceInterval.coerceAtLeast(1)
    val recurrence = when (recurrenceType) {
        "DAILY" -> RecurrenceRule.Daily(interval, endDate)
        "WEEKLY" -> RecurrenceRule.Weekly(
            days = DayOfWeek.entries.filterTo(mutableSetOf()) { day ->
                recurrenceWeekdaysMask and (1 shl (day.value - 1)) != 0
            }.ifEmpty { setOf(startDate.dayOfWeek) },
            interval = interval,
            endDate = endDate,
        )
        "MONTHLY" -> RecurrenceRule.Monthly(interval, endDate)
        "YEARLY" -> RecurrenceRule.Yearly(interval, endDate)
        else -> RecurrenceRule.None
    }

    return PlannerItem(
        id = id,
        title = title,
        description = description,
        type = PlannerItemType.entries.firstOrNull { it.name.equals(type, ignoreCase = true) }
            ?: PlannerItemType.Task,
        startDate = startDate,
        time = timeMinutes?.let { LocalTime.of(it / 60, it % 60) },
        isAllDay = isAllDay,
        reminder = reminderMinutesBefore?.let { minutes ->
            ReminderOffset.entries.firstOrNull { it.minutesBefore == minutes }
        },
        recurrence = recurrence,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )
}

