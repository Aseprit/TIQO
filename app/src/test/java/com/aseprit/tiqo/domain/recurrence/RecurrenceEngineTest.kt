package com.aseprit.tiqo.domain.recurrence

import com.aseprit.tiqo.domain.model.PlannerItem
import com.aseprit.tiqo.domain.model.RecurrenceRule
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurrenceEngineTest {
    private val start = LocalDate.of(2026, 9, 1)

    @Test fun daily_occursEveryDay() {
        val item = item(RecurrenceRule.Daily())
        assertTrue(RecurrenceEngine.occursOn(item, start.plusDays(17)))
    }

    @Test fun selectedWeekdays_onlyOccurOnSelectedDays() {
        val item = item(RecurrenceRule.Weekly(setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)))
        assertTrue(RecurrenceEngine.occursOn(item, LocalDate.of(2026, 9, 3)))
        assertFalse(RecurrenceEngine.occursOn(item, LocalDate.of(2026, 9, 4)))
    }

    @Test fun everyTwoWeeks_skipsAlternatingWeek() {
        val item = item(RecurrenceRule.Weekly(setOf(DayOfWeek.TUESDAY), interval = 2))
        assertFalse(RecurrenceEngine.occursOn(item, start.plusWeeks(1)))
        assertTrue(RecurrenceEngine.occursOn(item, start.plusWeeks(2)))
    }

    @Test fun monthly_clampsToLastDayOfShortMonth() {
        val item = PlannerItem(title = "Koniec miesiąca", startDate = LocalDate.of(2026, 1, 31), recurrence = RecurrenceRule.Monthly())
        assertTrue(RecurrenceEngine.occursOn(item, LocalDate.of(2026, 2, 28)))
    }

    @Test fun yearly_occursOnAnniversary() {
        val item = item(RecurrenceRule.Yearly())
        assertTrue(RecurrenceEngine.occursOn(item, LocalDate.of(2029, 9, 1)))
        assertFalse(RecurrenceEngine.occursOn(item, LocalDate.of(2029, 9, 2)))
    }

    private fun item(rule: RecurrenceRule) = PlannerItem(title = "Test", startDate = start, recurrence = rule)
}
