package com.aseprit.tiqo.domain.recurrence

import com.aseprit.tiqo.domain.model.PlannerItem
import com.aseprit.tiqo.domain.model.PlannerItemType
import com.aseprit.tiqo.domain.model.RecurrenceRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

object RecurrenceEngine {
    fun occursOn(item: PlannerItem, date: LocalDate): Boolean {
        if (date < item.startDate) return false
        if (item.recurrence.endDate?.let { date > it } == true) return false

        val rule = if (item.type == PlannerItemType.Birthday && item.recurrence == RecurrenceRule.None) {
            RecurrenceRule.Yearly()
        } else {
            item.recurrence
        }

        return when (rule) {
            RecurrenceRule.None -> date == item.startDate
            is RecurrenceRule.Daily -> {
                ChronoUnit.DAYS.between(item.startDate, date) % rule.safeInterval == 0L
            }
            is RecurrenceRule.Weekly -> {
                val startWeek = item.startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val dateWeek = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val weeks = ChronoUnit.WEEKS.between(startWeek, dateWeek)
                weeks % rule.safeInterval == 0L && date.dayOfWeek in rule.days
            }
            is RecurrenceRule.Monthly -> {
                val months = ChronoUnit.MONTHS.between(
                    YearMonth.from(item.startDate),
                    YearMonth.from(date),
                )
                val occurrenceDay = minOf(item.startDate.dayOfMonth, date.lengthOfMonth())
                months % rule.safeInterval == 0L && date.dayOfMonth == occurrenceDay
            }
            is RecurrenceRule.Yearly -> {
                val years = date.year - item.startDate.year
                val occurrenceDay = minOf(item.startDate.dayOfMonth, date.lengthOfMonth())
                years.toLong() % rule.safeInterval == 0L &&
                    date.month == item.startDate.month &&
                    date.dayOfMonth == occurrenceDay
            }
        }
    }

    fun occurrencesBetween(
        item: PlannerItem,
        startInclusive: LocalDate,
        endInclusive: LocalDate,
    ): List<LocalDate> {
        if (endInclusive < startInclusive) return emptyList()
        return generateSequence(startInclusive) { current ->
            current.plusDays(1).takeIf { it <= endInclusive }
        }.filter { occursOn(item, it) }.toList()
    }

    private val RecurrenceRule.safeInterval: Long
        get() = interval.coerceAtLeast(1).toLong()
}
