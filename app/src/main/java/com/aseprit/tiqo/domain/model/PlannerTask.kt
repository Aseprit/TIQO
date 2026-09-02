package com.aseprit.tiqo.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.MonthDay

data class PlannerTask(
    val id: Long,
    val title: String,
    val description: String = "",
    val date: LocalDate,
    val time: LocalTime? = null,
    val recurrence: RecurrenceRule = RecurrenceRule.Once,
    val isCompleted: Boolean = false,
)

sealed interface RecurrenceRule {
    data object Once : RecurrenceRule

    data class Weekly(val days: Set<DayOfWeek>) : RecurrenceRule

    data class Yearly(val day: MonthDay) : RecurrenceRule
}

